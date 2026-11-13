package com.example.ui.screens.voice

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.TransactionType
import com.example.data.model.UiState
import com.example.data.voice.ParsedVoiceCommand
import com.example.data.voice.VoiceCommandAction
import com.example.data.voice.VoiceCommandParser
import com.example.data.voice.VoiceParseError
import com.example.data.voice.VoiceParseResult
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors
import com.example.ui.viewmodel.WmsAppState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceCommandScreen(
    uiState: WmsAppState,
    onExecuteStockMovement: (
        partNumber: String,
        type: TransactionType,
        qty: Long,
        targetLocation: String,
        notes: String
    ) -> Unit,
    onExecuteCycleCount: (partNumber: String, actualQty: Long, notes: String) -> Unit,
    onOpenCycleCountForPart: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val wmsColors = MaterialTheme.wmsColors

    var transcript by rememberSaveable { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingConfirmation by remember { mutableStateOf<ParsedVoiceCommand?>(null) }

    val canTransact = uiState.activeRole.canTransact() && uiState.selectedFactoryId.isNotBlank()
    val materials = (uiState.materialsState as? UiState.Success)?.data.orEmpty()

    val micPermissionMsg = stringResource(R.string.voice_cmd_mic_permission)
    val voiceUnavailableMsg = stringResource(R.string.voice_search_unavailable)
    val errEmptyMsg = stringResource(R.string.voice_cmd_err_empty)
    val errActionMsg = stringResource(R.string.voice_cmd_err_action)
    val errPartMsg = stringResource(R.string.voice_cmd_err_part)
    val errQtyMsg = stringResource(R.string.voice_cmd_err_qty)

    val speechLocaleTag = remember(uiState.language) {
        if (uiState.language == AppLanguage.AR) "ar-EG" else "en-US"
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    errorMessage = null
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (error != SpeechRecognizer.ERROR_CLIENT &&
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                        error != SpeechRecognizer.ERROR_NO_MATCH
                    ) {
                        errorMessage = null
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!spoken.isNullOrBlank()) {
                        transcript = spoken
                        errorMessage = null
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!partial.isNullOrBlank()) {
                        transcript = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )
        onDispose {
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
        }
    }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                transcript = spoken
                errorMessage = null
            }
        }
    }

    fun launchSystemSpeechDialog() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocaleTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLocaleTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_cmd_listening))
        }
        try {
            systemSpeechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            errorMessage = voiceUnavailableMsg
        }
    }

    fun startInlineListening() {
        errorMessage = null
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (speechRecognizer == null) {
            launchSystemSpeechDialog()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocaleTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLocaleTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        isListening = true
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure {
                isListening = false
                launchSystemSpeechDialog()
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startInlineListening()
        } else {
            errorMessage = micPermissionMsg
        }
    }

    fun toggleListening() {
        if (isListening) {
            runCatching { speechRecognizer?.stopListening() }
            isListening = false
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                startInlineListening()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val micBgColor by animateColorAsState(
        targetValue = if (isListening) wmsColors.issueRed else MaterialTheme.colorScheme.primary,
        label = "micBg"
    )
    val micContentColor by animateColorAsState(
        targetValue = if (isListening) wmsColors.onIssueRed else MaterialTheme.colorScheme.onPrimary,
        label = "micContent"
    )

    val examples = listOf(
        stringResource(R.string.voice_cmd_ex_issue_en),
        stringResource(R.string.voice_cmd_ex_return_en),
        stringResource(R.string.voice_cmd_ex_issue_ar),
        stringResource(R.string.voice_cmd_ex_return_ar),
        stringResource(R.string.voice_cmd_ex_add_ar),
        stringResource(R.string.voice_cmd_ex_count_en)
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp)
                .align(Alignment.TopCenter)
                .testTag("voice_command_screen")
        ) {
            // Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("voice_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.voice_cmd_cancel)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.voice_cmd_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.voice_cmd_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!canTransact) {
                item {
                    Surface(
                        color = wmsColors.transferAmberContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, wmsColors.transferAmber),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.voice_cmd_viewer_blocked),
                            style = MaterialTheme.typography.bodyMedium,
                            color = wmsColors.transferAmber,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Large Animated Microphone Section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.5.dp,
                        if (isListening) wmsColors.issueRed else wmsColors.outline
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(136.dp)
                        ) {
                            if (isListening) {
                                Box(
                                    modifier = Modifier
                                        .size(124.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(wmsColors.issueRed.copy(alpha = 0.20f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(104.dp)
                                        .scale(1f + (pulseScale - 1f) * 0.5f)
                                        .clip(CircleShape)
                                        .background(wmsColors.issueRed.copy(alpha = 0.34f))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(116.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                                )
                            }

                            Surface(
                                onClick = { toggleListening() },
                                shape = CircleShape,
                                color = micBgColor,
                                contentColor = micContentColor,
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .size(88.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("voice_mic_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                        contentDescription = stringResource(R.string.voice_cmd_tap_to_speak),
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isListening) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = wmsColors.issueRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (isListening) R.string.voice_cmd_listening
                                    else R.string.voice_cmd_tap_to_speak
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isListening) wmsColors.issueRed else MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }

                        TextButton(
                            onClick = { launchSystemSpeechDialog() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("voice_system_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.voice_cmd_use_system_mic))
                        }
                    }
                }
            }

            // Large Editable Speech-to-Text Box
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = transcript,
                        onValueChange = {
                            transcript = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.voice_cmd_input_label)) },
                        placeholder = { Text(stringResource(R.string.voice_cmd_input_hint)) },
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                        minLines = 3,
                        maxLines = 5,
                        trailingIcon = {
                            if (transcript.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        transcript = ""
                                        errorMessage = null
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = wmsColors.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_transcript_input")
                    )

                    if (errorMessage != null) {
                        Surface(
                            color = wmsColors.issueRedContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, wmsColors.issueRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = wmsColors.issueRed,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Examples Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.voice_cmd_examples_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        examples.forEachIndexed { index, phrase ->
                            AssistChip(
                                onClick = {
                                    transcript = phrase
                                    errorMessage = null
                                },
                                label = {
                                    Text(
                                        text = phrase,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = wmsColors.surfaceElevated,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, wmsColors.outline),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("voice_example_chip_$index")
                            )
                        }
                    }
                }
            }

            // Confirm & Cancel Buttons
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !uiState.isSubmittingAction,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, wmsColors.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("voice_cancel_button")
                    ) {
                        Text(
                            text = stringResource(R.string.voice_cmd_cancel),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            when (val parsed = VoiceCommandParser.parse(transcript)) {
                                is VoiceParseResult.Failure -> {
                                    errorMessage = when (parsed.reason) {
                                        VoiceParseError.EMPTY_INPUT -> errEmptyMsg
                                        VoiceParseError.MISSING_ACTION -> errActionMsg
                                        VoiceParseError.MISSING_PART_NUMBER -> errPartMsg
                                        VoiceParseError.MISSING_QUANTITY -> errQtyMsg
                                    }
                                }
                                is VoiceParseResult.Success -> {
                                    errorMessage = null
                                    pendingConfirmation = parsed.command
                                }
                            }
                        },
                        enabled = canTransact && !uiState.isSubmittingAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = wmsColors.receiveGreen,
                            contentColor = wmsColors.onReceiveGreen
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(56.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("voice_confirm_button")
                    ) {
                        if (uiState.isSubmittingAction) {
                            CircularProgressIndicator(
                                color = wmsColors.onReceiveGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.voice_cmd_confirm),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    val confirmCmd = pendingConfirmation
    if (confirmCmd != null) {
        val actionLabel = when (confirmCmd.action) {
            VoiceCommandAction.ISSUE -> stringResource(R.string.voice_cmd_action_issue)
            VoiceCommandAction.RECEIVE -> stringResource(R.string.voice_cmd_action_receive)
            VoiceCommandAction.CYCLE_COUNT -> stringResource(R.string.voice_cmd_action_count)
        }
        val accentColor = when (confirmCmd.action) {
            VoiceCommandAction.ISSUE -> wmsColors.issueRed
            VoiceCommandAction.RECEIVE -> wmsColors.receiveGreen
            VoiceCommandAction.CYCLE_COUNT -> MaterialTheme.colorScheme.primary
        }
        val onAccentColor = when (confirmCmd.action) {
            VoiceCommandAction.ISSUE -> wmsColors.onIssueRed
            VoiceCommandAction.RECEIVE -> wmsColors.onReceiveGreen
            VoiceCommandAction.CYCLE_COUNT -> MaterialTheme.colorScheme.onPrimary
        }

        val matchedMaterial = remember(materials, confirmCmd.partNumber) {
            materials.firstOrNull { it.partNumber == confirmCmd.partNumber }
        }

        val promptMessage = if (confirmCmd.quantity != null) {
            stringResource(
                R.string.voice_cmd_dialog_message_qty,
                actionLabel,
                confirmCmd.quantity,
                confirmCmd.partNumber
            )
        } else {
            stringResource(
                R.string.voice_cmd_dialog_message_open_cc,
                confirmCmd.partNumber
            )
        }

        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = {
                Text(
                    text = stringResource(R.string.voice_cmd_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = promptMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (matchedMaterial != null) {
                        Surface(
                            color = wmsColors.surfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, wmsColors.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = matchedMaterial.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${matchedMaterial.location} · ${matchedMaterial.availableQty} ${matchedMaterial.unit}",
                                    style = WmsMonoTypography.location,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmation = null
                        val voiceNote = "Voice: ${confirmCmd.rawTranscript}"
                        when (confirmCmd.action) {
                            VoiceCommandAction.ISSUE -> {
                                onExecuteStockMovement(
                                    confirmCmd.partNumber,
                                    TransactionType.ISSUE,
                                    confirmCmd.quantity ?: 0L,
                                    "",
                                    voiceNote
                                )
                            }
                            VoiceCommandAction.RECEIVE -> {
                                onExecuteStockMovement(
                                    confirmCmd.partNumber,
                                    TransactionType.RECEIVE,
                                    confirmCmd.quantity ?: 0L,
                                    "",
                                    voiceNote
                                )
                            }
                            VoiceCommandAction.CYCLE_COUNT -> {
                                val qty = confirmCmd.quantity
                                if (qty != null) {
                                    onExecuteCycleCount(confirmCmd.partNumber, qty, voiceNote)
                                } else {
                                    onOpenCycleCountForPart(confirmCmd.partNumber)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = onAccentColor
                    ),
                    modifier = Modifier.testTag("voice_dialog_yes_button")
                ) {
                    Text(stringResource(R.string.voice_cmd_yes_execute))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }) {
                    Text(stringResource(R.string.voice_cmd_cancel))
                }
            }
        )
    }
}
