package com.example.ui.screens.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.ocr.OcrExtractedRow
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors
import com.example.ui.viewmodel.OcrImportUiState
import com.example.ui.viewmodel.OcrImportViewModel
import java.io.File

@Composable
fun OcrImportScreen(
    uiState: OcrImportUiState,
    viewModel: OcrImportViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val wmsColors = MaterialTheme.wmsColors
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showRawText by remember { mutableStateOf(false) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.processImageUri(context, uri)
        }
    }

    val cameraPermissionDeniedMsg = stringResource(R.string.scanner_camera_permission_rationale)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempOcrPhotoUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            viewModel.postError(cameraPermissionDeniedMsg)
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.processImageUri(context, uri)
        }
    }

    fun launchCameraCapture() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = createTempOcrPhotoUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp)
                .align(Alignment.TopCenter)
                .testTag("ocr_import_screen")
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
                            .testTag("ocr_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_dashboard)
                        )
                    }
                    Text(
                        text = stringResource(R.string.ocr_screen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Camera Capture + Gallery Picker Actions (Zero storage permissions via Photo Picker)
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { launchCameraCapture() },
                        enabled = !uiState.isProcessingOcr && !uiState.isSaving,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("ocr_take_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.ocr_take_photo),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            galleryPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !uiState.isProcessingOcr && !uiState.isSaving,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("ocr_pick_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.ocr_pick_gallery),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Loading State while ML Kit processes the photo
            if (uiState.isProcessingOcr) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ocr_loading_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = stringResource(R.string.ocr_processing),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Low confidence legend + Editable Preview Table
            if (uiState.extractedRows.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.ocr_preview_title, uiState.extractedRows.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        OutlinedButton(
                            onClick = { viewModel.addManualRow() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ocr_add_row_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.ocr_add_row))
                        }
                    }
                }

                if (uiState.extractedRows.any { it.hasLowConfidence }) {
                    item {
                        Surface(
                            color = wmsColors.transferAmberContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, wmsColors.transferAmber.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = wmsColors.transferAmber
                                )
                                Text(
                                    text = stringResource(R.string.ocr_low_conf_legend),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = wmsColors.transferAmber
                                )
                            }
                        }
                    }
                }

                // Editable Preview Table Header
                item {
                    val horizontalScrollState = rememberScrollState()
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, wmsColors.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ocr_preview_table")
                    ) {
                        Column(
                            modifier = Modifier
                                .horizontalScroll(horizontalScrollState)
                                .padding(12.dp)
                        ) {
                            // Column Header Strip
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .background(wmsColors.surfaceElevated, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ocr_col_part),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(134.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ocr_col_loc),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(114.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ocr_col_line),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(140.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ocr_col_qty),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(100.dp)
                                )
                                Spacer(modifier = Modifier.width(48.dp))
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            uiState.extractedRows.forEach { row ->
                                OcrEditableTableRow(
                                    row = row,
                                    availableLines = uiState.availableLines,
                                    onPartChange = { viewModel.updateRowPartNumber(row.rowId, it) },
                                    onLocationChange = { viewModel.updateRowLocation(row.rowId, it) },
                                    onLineChange = { viewModel.updateRowProductionLine(row.rowId, it) },
                                    onQtyChange = { viewModel.updateRowRequiredQty(row.rowId, it) },
                                    onDelete = { viewModel.removeRow(row.rowId) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Confirm & Save Button
                item {
                    Button(
                        onClick = { viewModel.confirmAndSaveToFirestore() },
                        enabled = !uiState.isSaving && !uiState.isProcessingOcr,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = wmsColors.receiveGreen,
                            contentColor = wmsColors.onReceiveGreen
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("ocr_confirm_save_button")
                    ) {
                        if (uiState.isSaving) {
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
                                text = stringResource(R.string.ocr_confirm_save),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (!uiState.isProcessingOcr) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, wmsColors.outline),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (uiState.hasAttemptedScan) R.string.ocr_no_rows_found
                                    else R.string.ocr_empty_hint
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = { viewModel.addManualRow() },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ocr_add_row))
                            }
                        }
                    }
                }
            }

            // Collapsible Raw OCR Text Inspector
            if (uiState.rawRecognizedText.isNotBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { showRawText = !showRawText },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(
                                stringResource(
                                    if (showRawText) R.string.ocr_hide_raw_text
                                    else R.string.ocr_show_raw_text
                                )
                            )
                        }
                        if (showRawText) {
                            Surface(
                                color = wmsColors.surfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, wmsColors.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.rawRecognizedText,
                                    style = WmsMonoTypography.location,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrEditableTableRow(
    row: OcrExtractedRow,
    availableLines: List<String>,
    onPartChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onLineChange: (String) -> Unit,
    onQtyChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var lineDropdownExpanded by remember { mutableStateOf(false) }
    val wmsColors = MaterialTheme.wmsColors

    @Composable
    fun cellColors(lowConfidence: Boolean) = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = if (lowConfidence) wmsColors.transferAmberContainer else wmsColors.surfaceElevated,
        unfocusedContainerColor = if (lowConfidence) wmsColors.transferAmberContainer else wmsColors.surfaceElevated,
        focusedBorderColor = if (lowConfidence) wmsColors.transferAmber else MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = if (lowConfidence) wmsColors.transferAmber else wmsColors.outline
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = row.partNumber,
            onValueChange = onPartChange,
            textStyle = WmsMonoTypography.partNumber,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = cellColors(row.lowConfidencePartNumber),
            modifier = Modifier
                .width(134.dp)
                .testTag("ocr_cell_part_${row.rowId}")
        )

        OutlinedTextField(
            value = row.location,
            onValueChange = onLocationChange,
            textStyle = WmsMonoTypography.location,
            singleLine = true,
            colors = cellColors(row.lowConfidenceLocation),
            modifier = Modifier
                .width(114.dp)
                .testTag("ocr_cell_loc_${row.rowId}")
        )

        ExposedDropdownMenuBox(
            expanded = lineDropdownExpanded,
            onExpandedChange = { lineDropdownExpanded = !lineDropdownExpanded },
            modifier = Modifier.width(140.dp)
        ) {
            OutlinedTextField(
                value = row.productionLine,
                onValueChange = onLineChange,
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = lineDropdownExpanded)
                },
                colors = cellColors(row.lowConfidenceLine),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
                    .testTag("ocr_cell_line_${row.rowId}")
            )
            ExposedDropdownMenu(
                expanded = lineDropdownExpanded,
                onDismissRequest = { lineDropdownExpanded = false }
            ) {
                availableLines.forEach { lineOption ->
                    DropdownMenuItem(
                        text = { Text(lineOption) },
                        onClick = {
                            onLineChange(lineOption)
                            lineDropdownExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = row.requiredQty,
            onValueChange = onQtyChange,
            textStyle = WmsMonoTypography.quantityMedium,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = cellColors(row.lowConfidenceQty),
            modifier = Modifier
                .width(100.dp)
                .testTag("ocr_cell_qty_${row.rowId}")
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("ocr_delete_row_${row.rowId}")
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.dialog_cancel),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun createTempOcrPhotoUri(context: Context): Uri {
    val ocrDir = File(context.cacheDir, "ocr").apply { mkdirs() }
    val photoFile = File(ocrDir, "ocr_capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}
