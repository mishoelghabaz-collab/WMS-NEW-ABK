package com.example.ui.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.Material
import com.example.data.model.MaterialStatus
import com.example.data.model.TransactionType
import com.example.data.model.UiState
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors
import com.example.ui.viewmodel.WmsAppState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun ScannerScreen(
    uiState: WmsAppState,
    onExecuteStockMovement: (
        partNumber: String,
        type: TransactionType,
        qty: Long,
        targetLocation: String,
        notes: String
    ) -> Unit,
    onOpenCycleCount: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val wmsColors = MaterialTheme.wmsColors

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isContinuousActive by remember { mutableStateOf(true) }
    var lastScannedCode by remember { mutableStateOf("") }
    var lastScanMillis by remember { mutableLongStateOf(0L) }
    var manualInput by remember { mutableStateOf("") }
    val scanHistory = remember { mutableStateListOf<String>() }
    var pendingAction by remember { mutableStateOf<Pair<Material, TransactionType>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { toneGenerator?.release() }
        }
    }

    val materials = (uiState.materialsState as? UiState.Success)?.data.orEmpty()
    val matchedMaterial = remember(materials, lastScannedCode) {
        if (lastScannedCode.isBlank()) null
        else materials.firstOrNull { it.partNumber == lastScannedCode }
    }
    val canTransact = uiState.activeRole.canTransact()

    fun registerScannedCode(rawCode: String, withFeedback: Boolean) {
        val cleaned = rawCode.trim()
        if (cleaned.isBlank()) return
        if (withFeedback) {
            runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        lastScannedCode = cleaned
        if (scanHistory.firstOrNull() != cleaned) {
            scanHistory.remove(cleaned)
            scanHistory.add(0, cleaned)
            if (scanHistory.size > 15) {
                scanHistory.removeAt(scanHistory.lastIndex)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp)
                .align(Alignment.TopCenter)
                .testTag("scanner_screen")
        ) {
            // Header with Back + Continuous Mode Toggle
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("scanner_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_dashboard)
                            )
                        }
                        Text(
                            text = stringResource(R.string.scanner_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilterChip(
                        selected = isContinuousActive,
                        onClick = { isContinuousActive = !isContinuousActive },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isContinuousActive) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(
                                    if (isContinuousActive) R.string.scanner_continuous_label
                                    else R.string.scanner_paused_label
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = wmsColors.receiveGreenContainer,
                            selectedLabelColor = wmsColors.receiveGreen,
                            selectedLeadingIconColor = wmsColors.receiveGreen
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("scanner_continuous_toggle")
                    )
                }
            }

            // CameraX + ML Kit Preview
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.5.dp,
                        if (isContinuousActive) wmsColors.receiveGreen.copy(alpha = 0.65f) else wmsColors.outline
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (hasCameraPermission) {
                        val barcodeScanner = remember {
                            val options = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                                .build()
                            BarcodeScanning.getClient(options)
                        }
                        val cameraController = remember(context) {
                            LifecycleCameraController(context).apply {
                                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                            }
                        }

                        DisposableEffect(lifecycleOwner, cameraController) {
                            val executor = ContextCompat.getMainExecutor(context)
                            cameraController.setImageAnalysisAnalyzer(
                                executor,
                                MlKitAnalyzer(
                                    listOf(barcodeScanner),
                                    CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                                    executor
                                ) { result ->
                                    if (!isContinuousActive) return@MlKitAnalyzer
                                    val barcodes = result.getValue(barcodeScanner).orEmpty()
                                    val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.trim()
                                    if (!raw.isNullOrBlank()) {
                                        val now = System.currentTimeMillis()
                                        if (raw != lastScannedCode || now - lastScanMillis > 1600L) {
                                            lastScanMillis = now
                                            registerScannedCode(raw, withFeedback = true)
                                        }
                                    }
                                }
                            )
                            cameraController.bindToLifecycle(lifecycleOwner)
                            onDispose {
                                cameraController.clearImageAnalysisAnalyzer()
                                cameraController.unbind()
                                runCatching { barcodeScanner.close() }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewView(ctx).apply { controller = cameraController }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, wmsColors.receiveGreen.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.scanner_manual_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = wmsColors.receiveGreen,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = stringResource(R.string.scanner_camera_permission_rationale),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(stringResource(R.string.scanner_grant_permission))
                            }
                        }
                    }
                }
            }

            // Manual 8-digit lookup fallback
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it.filter { ch -> ch.isDigit() }.take(8) },
                        label = { Text(stringResource(R.string.scanner_manual_lookup)) },
                        textStyle = WmsMonoTypography.partNumber,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scanner_manual_input")
                    )
                    Button(
                        onClick = {
                            if (manualInput.isNotBlank()) {
                                registerScannedCode(manualInput, withFeedback = false)
                            }
                        },
                        enabled = manualInput.length == 8,
                        modifier = Modifier
                            .height(56.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("scanner_manual_find_button")
                    ) {
                        Text(stringResource(R.string.scanner_lookup_button))
                    }
                }
            }

            // Live Firestore Result for Scanned Barcode
            if (lastScannedCode.isNotBlank()) {
                item {
                    if (matchedMaterial != null) {
                        ScannedMaterialResultCard(
                            material = matchedMaterial,
                            canTransact = canTransact,
                            onReceive = { pendingAction = matchedMaterial to TransactionType.RECEIVE },
                            onIssue = { pendingAction = matchedMaterial to TransactionType.ISSUE },
                            onTransfer = { pendingAction = matchedMaterial to TransactionType.TRANSFER },
                            onCycleCount = { onOpenCycleCount(matchedMaterial.partNumber) }
                        )
                    } else {
                        Surface(
                            color = wmsColors.transferAmberContainer,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, wmsColors.transferAmber),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.scanner_not_found_in_plant,
                                    lastScannedCode,
                                    uiState.selectedFactoryId.ifBlank { "--" }
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = wmsColors.transferAmber,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            // Scan Session History
            if (scanHistory.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.scanner_last_scanned),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(scanHistory, key = { it }) { code ->
                    Surface(
                        onClick = { lastScannedCode = code },
                        color = wmsColors.surfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (code == lastScannedCode) MaterialTheme.colorScheme.primary else wmsColors.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = code,
                                style = WmsMonoTypography.partNumber,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val item = materials.firstOrNull { it.partNumber == code }
                            Text(
                                text = item?.let { "${it.availableQty} ${it.unit} · ${it.location}" }
                                    ?: "--",
                                style = WmsMonoTypography.location,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    val action = pendingAction
    if (action != null) {
        ScannerStockDialog(
            material = action.first,
            type = action.second,
            isSubmitting = uiState.isSubmittingAction,
            onDismiss = { pendingAction = null },
            onConfirm = { qty, targetLocation, notes ->
                onExecuteStockMovement(
                    action.first.partNumber,
                    action.second,
                    qty,
                    targetLocation,
                    notes
                )
                pendingAction = null
            }
        )
    }
}

@Composable
private fun ScannedMaterialResultCard(
    material: Material,
    canTransact: Boolean,
    onReceive: () -> Unit,
    onIssue: () -> Unit,
    onTransfer: () -> Unit,
    onCycleCount: () -> Unit
) {
    val wmsColors = MaterialTheme.wmsColors
    val statusColor = when (material.statusEnum()) {
        MaterialStatus.IN_STOCK -> wmsColors.statusInStock
        MaterialStatus.LOW_STOCK -> wmsColors.statusLowStock
        MaterialStatus.OUT_OF_STOCK -> wmsColors.statusOutOfStock
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scanned_material_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = material.partNumber,
                    style = WmsMonoTypography.partNumber.copy(fontSize = 24.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = wmsColors.surfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = material.location,
                            style = WmsMonoTypography.location,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = "${material.name} · ${material.productionLine}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "${material.availableQty} ${material.unit}",
                style = WmsMonoTypography.quantityLarge,
                color = statusColor
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onReceive,
                    enabled = canTransact,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = wmsColors.receiveGreen,
                        contentColor = wmsColors.onReceiveGreen
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.inventory_btn_receive), maxLines = 1)
                }
                Button(
                    onClick = onIssue,
                    enabled = canTransact && material.availableQty > 0L,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = wmsColors.issueRed,
                        contentColor = wmsColors.onIssueRed
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.inventory_btn_issue), maxLines = 1)
                }
                Button(
                    onClick = onTransfer,
                    enabled = canTransact,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = wmsColors.transferAmber,
                        contentColor = wmsColors.onTransferAmber
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.inventory_btn_transfer), maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = onCycleCount,
                enabled = canTransact,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.FactCheck,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.scanner_count_now))
            }
        }
    }
}

@Composable
private fun ScannerStockDialog(
    material: Material,
    type: TransactionType,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (qty: Long, targetLocation: String, notes: String) -> Unit
) {
    var qtyText by remember { mutableStateOf("1") }
    var targetLocation by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val positiveQtyError = stringResource(R.string.error_positive_qty)
    val targetLocError = stringResource(R.string.error_target_location_required)
    val insufficientTemplate = stringResource(R.string.error_insufficient_stock)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = "${type.name} · ${material.partNumber}",
                style = WmsMonoTypography.partNumber
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = {
                        qtyText = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                    },
                    label = { Text(stringResource(R.string.dialog_quantity)) },
                    textStyle = WmsMonoTypography.quantityMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == TransactionType.TRANSFER) {
                    OutlinedTextField(
                        value = targetLocation,
                        onValueChange = {
                            targetLocation = it.uppercase()
                            errorMsg = null
                        },
                        label = { Text(stringResource(R.string.dialog_target_location)) },
                        textStyle = WmsMonoTypography.location,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.dialog_notes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.toLongOrNull() ?: 0L
                    when {
                        qty <= 0L -> errorMsg = positiveQtyError
                        type == TransactionType.ISSUE && qty > material.availableQty ->
                            errorMsg = String.format(insufficientTemplate, qty, material.availableQty)
                        type == TransactionType.TRANSFER && targetLocation.isBlank() ->
                            errorMsg = targetLocError
                        else -> onConfirm(qty, targetLocation, notes)
                    }
                },
                enabled = !isSubmitting
            ) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
