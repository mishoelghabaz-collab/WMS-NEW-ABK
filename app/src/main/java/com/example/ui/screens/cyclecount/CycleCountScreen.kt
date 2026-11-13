package com.example.ui.screens.cyclecount

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CycleCountRecord
import com.example.data.model.Material
import com.example.data.model.UiState
import com.example.ui.components.BarcodeScanDialog
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors
import com.example.ui.viewmodel.CycleCountUiState
import com.example.ui.viewmodel.CycleCountViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun CycleCountScreen(
    uiState: CycleCountUiState,
    viewModel: CycleCountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val wmsColors = MaterialTheme.wmsColors
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showScannerDialog by remember { mutableStateOf(false) }

    val matchingSuggestions = remember(uiState.materials, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else uiState.materials.filter {
            it.partNumber.contains(q, ignoreCase = true) ||
                it.name.contains(q, ignoreCase = true)
        }.take(5)
    }

    val dateFormatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp)
                .align(Alignment.TopCenter)
                .testTag("cycle_count_screen")
        ) {
            // Enterprise Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cycle_count_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_dashboard)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.cc_screen_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (uiState.activeFactoryId.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = uiState.activeFactoryId,
                                style = WmsMonoTypography.location,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Scan + Search Command Bar Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, wmsColors.outline),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Button(
                            onClick = { showScannerDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = wmsColors.receiveGreen,
                                contentColor = wmsColors.onReceiveGreen
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier
                                .height(58.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("cycle_count_scan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QrCodeScanner,
                                contentDescription = stringResource(R.string.cc_scan_cta),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.cc_scan_cta),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { input ->
                                searchQuery = input
                                val trimmed = input.trim()
                                if (Material.isValidPartNumber(trimmed)) {
                                    viewModel.selectOrLookupPart(trimmed)
                                }
                            },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.cc_search_hint),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.cd_clear_search)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = wmsColors.surfaceElevated,
                                unfocusedContainerColor = wmsColors.surfaceElevated,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = wmsColors.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cycle_count_search_input")
                        )
                    }
                }
            }

            // Quick Search Matches when typing partial part number or material name
            if (uiState.activeMaterial == null && matchingSuggestions.isNotEmpty()) {
                item {
                    Surface(
                        color = wmsColors.surfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            matchingSuggestions.forEach { candidate ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .testTag("cc_suggestion_${candidate.partNumber}")
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.partNumber,
                                            style = WmsMonoTypography.partNumber.copy(fontSize = 18.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${candidate.name} · ${candidate.location}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            searchQuery = candidate.partNumber
                                            viewModel.selectOrLookupPart(candidate.partNumber)
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text(stringResource(R.string.select_mode_enable))
                                    }
                                }
                                HorizontalDivider(color = wmsColors.outline.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }

            if (uiState.notFoundPartNumber != null && uiState.activeMaterial == null) {
                item {
                    Surface(
                        color = wmsColors.transferAmberContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, wmsColors.transferAmber),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(
                                R.string.cc_item_not_found,
                                uiState.notFoundPartNumber,
                                uiState.activeFactoryId.ifBlank { "--" }
                            ),
                            color = wmsColors.transferAmber,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Active Cycle Count Workspace (Enterprise Typography + Instant Variance + Tactile Keypad)
            val activeMat = uiState.activeMaterial
            if (activeMat != null) {
                item {
                    ActiveCycleCountPanel(
                        material = activeMat,
                        actualQtyInput = uiState.actualQtyInput,
                        variance = uiState.variance,
                        adjustmentNotes = uiState.adjustmentNotes,
                        isSubmitting = uiState.isSubmitting,
                        onDigitPress = viewModel::appendDigit,
                        onBackspace = viewModel::backspaceDigit,
                        onClear = viewModel::clearDigits,
                        onNotesChange = viewModel::updateAdjustmentNotes,
                        onSubmit = viewModel::submitCount,
                        onClearActive = {
                            searchQuery = ""
                            viewModel.clearActiveCount()
                        }
                    )
                }
            }

            // Recently Counted Items (Real-time Firestore cycleCounts)
            item {
                Text(
                    text = stringResource(R.string.cc_recent_counts),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            when (val countsState = uiState.recentCountsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(text = countsState.message, color = wmsColors.issueRed)
                    }
                }
                is UiState.Success -> {
                    if (countsState.data.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, wmsColors.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.cc_no_recent_counts),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(countsState.data.take(20), key = { it.id }) { record ->
                            RecentCycleCountCard(
                                record = record,
                                dateFormatter = dateFormatter,
                                onTapToRecount = {
                                    searchQuery = record.partNumber
                                    viewModel.selectOrLookupPart(record.partNumber)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showScannerDialog) {
        BarcodeScanDialog(
            onBarcodeScanned = { scanned ->
                val clean = scanned.trim()
                searchQuery = clean
                viewModel.selectOrLookupPart(clean)
                showScannerDialog = false
            },
            onDismiss = { showScannerDialog = false }
        )
    }
}

@Composable
private fun ActiveCycleCountPanel(
    material: Material,
    actualQtyInput: String,
    variance: Long?,
    adjustmentNotes: String,
    isSubmitting: Boolean,
    onDigitPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onNotesChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearActive: () -> Unit
) {
    val wmsColors = MaterialTheme.wmsColors
    val isMatch = variance == 0L
    val hasVariance = variance != null && variance != 0L

    val panelBorderColor by animateColorAsState(
        targetValue = when {
            isMatch -> wmsColors.receiveGreen
            hasVariance -> wmsColors.issueRed
            else -> MaterialTheme.colorScheme.primary
        },
        label = "panelBorder"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, panelBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cycle_count_active_panel")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            // Enterprise SKU Banner: Bold 30.sp Monospace Part Number + Bin Tag
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = material.partNumber,
                        style = WmsMonoTypography.partNumber.copy(
                            fontFamily = RobotoMonoFontFamily,
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.6.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${material.name} · ${material.productionLine}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = material.location,
                            style = WmsMonoTypography.location,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Side-by-Side Bold Telemetry Cards: System Qty vs Actual Physical Qty
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = wmsColors.surfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, wmsColors.outline),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cc_system_qty).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = material.availableQty.toString(),
                            style = WmsMonoTypography.quantityLarge.copy(
                                fontSize = 36.sp,
                                lineHeight = 40.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = material.unit,
                            style = WmsMonoTypography.location,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = wmsColors.surfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, panelBorderColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cc_actual_qty).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = actualQtyInput.ifBlank { "--" },
                            style = WmsMonoTypography.quantityLarge.copy(
                                fontSize = 36.sp,
                                lineHeight = 40.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = if (actualQtyInput.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.testTag("cc_actual_qty_display")
                        )
                        Text(
                            text = material.unit,
                            style = WmsMonoTypography.location,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Instant Variance Strip (Green on 0, Red on != 0)
            if (variance != null) {
                val bg by animateColorAsState(
                    targetValue = if (isMatch) wmsColors.receiveGreenContainer else wmsColors.issueRedContainer,
                    label = "varBg"
                )
                val fg by animateColorAsState(
                    targetValue = if (isMatch) wmsColors.receiveGreen else wmsColors.issueRed,
                    label = "varFg"
                )
                val varianceText = if (variance > 0) "+$variance" else variance.toString()

                Surface(
                    color = bg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, fg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cc_variance_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isMatch) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = fg,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = if (isMatch) {
                                        stringResource(R.string.cc_match_verified)
                                    } else {
                                        stringResource(R.string.cc_variance_warning)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = fg
                                )
                                if (hasVariance) {
                                    val actualParsed = actualQtyInput.toLongOrNull() ?: material.availableQty
                                    Text(
                                        text = stringResource(R.string.cc_new_stock_preview, actualParsed, material.unit),
                                        style = WmsMonoTypography.location,
                                        fontWeight = FontWeight.SemiBold,
                                        color = fg
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = fg.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, fg.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = varianceText,
                                style = WmsMonoTypography.quantityLarge.copy(
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = fg,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Custom Enterprise Numeric Keypad (Tactile + Haptic)
            FastNumericKeypad(
                onDigit = onDigitPress,
                onBackspace = onBackspace,
                onClear = onClear
            )

            // Discrepancy Note when Variance != 0
            if (hasVariance) {
                OutlinedTextField(
                    value = adjustmentNotes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.cc_adjustment_notes_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cc_adjustment_notes_input")
                )
            }

            // Distinct Action Bar: Elevated Gradient Submit Adjustment (or Verified Green Save)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onClearActive,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, wmsColors.outline),
                    modifier = Modifier
                        .height(60.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.dialog_cancel))
                }

                if (hasVariance) {
                    // Distinct Enterprise "Submit Adjustment" CTA
                    val varianceTag = if ((variance ?: 0L) > 0L) "+$variance" else "$variance"
                    Surface(
                        onClick = { if (!isSubmitting) onSubmit() },
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.5.dp, wmsColors.issueRed),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("cc_submit_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            wmsColors.issueRed,
                                            wmsColors.issueRed.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                                .padding(horizontal = 14.dp)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    color = wmsColors.onIssueRed,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = null,
                                        tint = wmsColors.onIssueRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.cc_submit_adjustment),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = wmsColors.onIssueRed,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        color = wmsColors.onIssueRed.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = varianceTag,
                                            style = WmsMonoTypography.location,
                                            fontWeight = FontWeight.Bold,
                                            color = wmsColors.onIssueRed,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    ElevatedButton(
                        onClick = onSubmit,
                        enabled = variance != null && !isSubmitting,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = wmsColors.receiveGreen,
                            contentColor = wmsColors.onReceiveGreen
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("cc_submit_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = wmsColors.onReceiveGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.FactCheck,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.cc_save_instant),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FastNumericKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val wmsColors = MaterialTheme.wmsColors
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("CLR", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cc_numeric_keypad")
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { key ->
                    val modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("cc_key_${key.lowercase()}")
                    when (key) {
                        "CLR" -> FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClear()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = wmsColors.transferAmberContainer,
                                contentColor = wmsColors.transferAmber
                            ),
                            modifier = modifier
                        ) {
                            Text(
                                text = "C",
                                style = WmsMonoTypography.quantityMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        "DEL" -> FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBackspace()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = wmsColors.issueRedContainer,
                                contentColor = wmsColors.issueRed
                            ),
                            modifier = modifier
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = stringResource(R.string.cd_clear_search)
                            )
                        }
                        else -> Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigit(key.first())
                            },
                            color = wmsColors.surfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, wmsColors.outline),
                            tonalElevation = 2.dp,
                            modifier = modifier
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = key,
                                    style = WmsMonoTypography.quantityLarge.copy(
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCycleCountCard(
    record: CycleCountRecord,
    dateFormatter: DateFormat,
    onTapToRecount: () -> Unit
) {
    val wmsColors = MaterialTheme.wmsColors
    val isMatch = record.variance == 0L
    val accent = if (isMatch) wmsColors.receiveGreen else wmsColors.issueRed
    val bg = if (isMatch) wmsColors.receiveGreenContainer else wmsColors.issueRedContainer
    val variancePrefix = if (record.variance > 0) "+" else ""
    val formattedDate = remember(record.timestamp) {
        record.timestamp?.toDate()?.let { dateFormatter.format(it) } ?: dateFormatter.format(Date())
    }

    Card(
        onClick = onTapToRecount,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isMatch) wmsColors.outline else wmsColors.issueRed.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cc_recent_${record.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = record.partNumber,
                        style = WmsMonoTypography.partNumber,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (isMatch) R.string.cc_status_matched else R.string.cc_status_adjusted
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${record.materialName} · ${record.location}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.countedBy} · $formattedDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${record.systemQty} -> ${record.actualQty}",
                    style = WmsMonoTypography.quantityMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isMatch) "0" else "$variancePrefix${record.variance}",
                    style = WmsMonoTypography.location,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }
    }
}
