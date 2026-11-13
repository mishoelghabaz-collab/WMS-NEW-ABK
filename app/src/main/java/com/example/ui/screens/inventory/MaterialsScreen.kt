package com.example.ui.screens.inventory

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.Material
import com.example.data.model.MaterialStatus
import com.example.data.model.TransactionType
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.ui.components.BarcodeScanDialog
import com.example.ui.screens.dashboard.RegisterMaterialDialog
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors
import com.example.ui.viewmodel.MaterialSortOption
import com.example.ui.viewmodel.MaterialsUiState
import com.example.ui.viewmodel.MaterialsViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(
    uiState: MaterialsUiState,
    userRole: UserRole,
    language: AppLanguage,
    viewModel: MaterialsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val wmsColors = MaterialTheme.wmsColors

    var searchInput by rememberSaveable { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showSortFilterSheet by remember { mutableStateOf(false) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var activeMaterialAction by remember { mutableStateOf<Pair<Material, TransactionType>?>(null) }
    var showBulkTransferDialog by remember { mutableStateOf(false) }
    var showBulkIssueDialog by remember { mutableStateOf(false) }

    val canTransact = userRole.canTransact()
    val canManageMaterials = userRole.canManageMaterials()

    // 300ms debounce so search input does not spam query evaluations on every keystroke
    LaunchedEffect(searchInput) {
        delay(300L)
        viewModel.updateDebouncedQuery(searchInput)
    }

    val voiceUnavailableMessage = stringResource(R.string.voice_search_unavailable)
    val voicePrompt = stringResource(R.string.voice_search_prompt)

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                val normalized = normalizeVoiceQuery(spoken)
                searchInput = normalized
                viewModel.updateDebouncedQuery(normalized)
                if (Material.isValidPartNumber(normalized)) {
                    viewModel.recordRecentScan(normalized)
                }
            }
        }
    }

    val materials = (uiState.materialsState as? UiState.Success)?.data.orEmpty()
    val selectedMaterials = remember(materials, uiState.selectedPartNumbers) {
        materials.filter { it.partNumber in uiState.selectedPartNumbers }
    }
    val hasActiveSortOrStatusFilter =
        uiState.sortOption != MaterialSortOption.PART_NUMBER_ASC || uiState.selectedStatusFilter != null

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 680.dp) 2 else 1

        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp)
                .align(Alignment.TopCenter)
                .testTag("materials_screen")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Prominent Warehouse Search Bar + Voice + Sort/Filter + Recent Scans Dropdown
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .zIndex(2f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = searchInput,
                                onValueChange = { searchInput = it },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.materials_search_label),
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (searchInput.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    searchInput = ""
                                                    viewModel.updateDebouncedQuery("")
                                                },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("materials_search_clear")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = stringResource(R.string.cd_clear_search)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { showScannerDialog = true },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("materials_search_scanner")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.QrCodeScanner,
                                                contentDescription = stringResource(R.string.cd_barcode_scanner),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = wmsColors.outline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        isSearchFocused = focusState.isFocused
                                    }
                                    .testTag("materials_search_input")
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.code)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                                    }
                                    try {
                                        voiceSearchLauncher.launch(speechIntent)
                                    } catch (_: ActivityNotFoundException) {
                                        viewModel.postFeedback(voiceUnavailableMessage, isError = true)
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = wmsColors.surfaceElevated,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .size(50.dp)
                                    .testTag("materials_voice_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = stringResource(R.string.cd_voice_search)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { showSortFilterSheet = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (hasActiveSortOrStatusFilter) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        wmsColors.surfaceElevated
                                    },
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .size(50.dp)
                                    .testTag("materials_sort_filter_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (hasActiveSortOrStatusFilter) {
                                            Badge(containerColor = wmsColors.transferAmber)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = stringResource(R.string.cd_sort_filter)
                                    )
                                }
                            }
                        }

                        // Recent Scans Dropdown (shown when search bar is empty & focused)
                        AnimatedVisibility(
                            visible = isSearchFocused && searchInput.isEmpty() && uiState.recentScans.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            RecentScansDropdownCard(
                                recentScans = uiState.recentScans,
                                onSelectPart = { partNumber ->
                                    searchInput = partNumber
                                    viewModel.updateDebouncedQuery(partNumber)
                                    viewModel.recordRecentScan(partNumber)
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            )
                        }
                    }
                }

                // 2. Production Line Filter Chips + Select Mode Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inventory_line_filter_row")
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.selectedLine == null,
                                onClick = { viewModel.selectLine(null) },
                                label = {
                                    Text(
                                        text = stringResource(R.string.filter_all),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("filter_chip_all")
                            )
                        }

                        items(uiState.availableLines, key = { it }) { line ->
                            val isSelected = uiState.selectedLine == line
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectLine(if (isSelected) null else line) },
                                label = {
                                    Text(
                                        text = line,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("filter_chip_${line.lowercase().replace(" ", "_")}")
                            )
                        }
                    }

                    FilterChip(
                        selected = uiState.isSelectionMode,
                        onClick = { viewModel.toggleSelectionMode() },
                        leadingIcon = {
                            Icon(
                                imageVector = if (uiState.isSelectionMode) {
                                    Icons.Filled.Check
                                } else {
                                    Icons.Filled.CheckBox
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(
                                    if (uiState.isSelectionMode) R.string.select_mode_done else R.string.select_mode_enable
                                ),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("materials_select_mode_toggle")
                    )
                }

                // 3. Real-Time Firestore Materials List
                when (val state = uiState.materialsState) {
                    is UiState.Loading -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            Surface(
                                color = wmsColors.issueRedContainer,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, wmsColors.issueRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = wmsColors.issueRed,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        val list = state.data
                        if (list.isEmpty()) {
                            EmptyMaterialsStateView(
                                searchTerm = uiState.debouncedQuery,
                                canRegister = canManageMaterials && uiState.activeFactoryId.isNotBlank(),
                                onScanBarcodeClick = { showScannerDialog = true },
                                onAddItemClick = { showRegisterDialog = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val bottomInsetPadding =
                                if (uiState.isSelectionMode && uiState.selectedPartNumbers.isNotEmpty()) 196.dp
                                else 156.dp

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = bottomInsetPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = list,
                                    key = { it.id.ifBlank { "${it.factoryId}_${it.partNumber}" } }
                                ) { material ->
                                    val isSelected = material.partNumber in uiState.selectedPartNumbers
                                    MaterialCard(
                                        material = material,
                                        canTransact = canTransact,
                                        isSelectionMode = uiState.isSelectionMode,
                                        isSelected = isSelected,
                                        onCardClick = {
                                            if (uiState.isSelectionMode) {
                                                viewModel.toggleSelectMaterial(material.partNumber)
                                            } else {
                                                viewModel.recordRecentScan(material.partNumber)
                                            }
                                        },
                                        onCardLongClick = {
                                            viewModel.enterSelectionModeWith(material.partNumber)
                                        },
                                        onToggleSelection = {
                                            viewModel.toggleSelectMaterial(material.partNumber)
                                        },
                                        onReceiveClick = {
                                            activeMaterialAction = material to TransactionType.RECEIVE
                                        },
                                        onIssueClick = {
                                            activeMaterialAction = material to TransactionType.ISSUE
                                        },
                                        onTransferClick = {
                                            activeMaterialAction = material to TransactionType.TRANSFER
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bulk Actions Bottom Bar (positioned above Voice Command FAB)
            AnimatedVisibility(
                visible = uiState.isSelectionMode && selectedMaterials.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 84.dp)
            ) {
                BulkActionsBar(
                    selectedCount = selectedMaterials.size,
                    canTransact = canTransact,
                    isSubmitting = uiState.isSubmitting,
                    onBulkTransferClick = { showBulkTransferDialog = true },
                    onBulkIssueClick = { showBulkIssueDialog = true },
                    onExportCsvClick = {
                        val intent = viewModel.buildCsvExportIntent(context, selectedMaterials)
                        if (intent != null) {
                            context.startActivity(intent)
                        }
                    },
                    onClearSelection = { viewModel.clearSelection() }
                )
            }

            // Add SKU FAB (stacked cleanly above the global Voice Command FAB)
            if (canManageMaterials &&
                uiState.activeFactoryId.isNotBlank() &&
                !(uiState.isSelectionMode && selectedMaterials.isNotEmpty())
            ) {
                FloatingActionButton(
                    onClick = { showRegisterDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp)
                        .testTag("inventory_add_sku_fab")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.inventory_add_sku)
                    )
                }
            }
        }
    }

    // Barcode Scanner Dialog -> directly populates Search Bar + Recent Scans
    if (showScannerDialog) {
        BarcodeScanDialog(
            onBarcodeScanned = { scannedValue ->
                val clean = scannedValue.trim()
                searchInput = clean
                viewModel.updateDebouncedQuery(clean)
                viewModel.recordRecentScan(clean)
                showScannerDialog = false
                focusManager.clearFocus()
            },
            onDismiss = { showScannerDialog = false }
        )
    }

    // Sort & Status Filter Bottom Sheet
    if (showSortFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSortFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("materials_sort_sheet")
        ) {
            SortAndFilterSheetContent(
                selectedSortOption = uiState.sortOption,
                selectedStatusFilter = uiState.selectedStatusFilter,
                onSelectSort = { option ->
                    viewModel.selectSortOption(option)
                    showSortFilterSheet = false
                },
                onSelectStatus = { status ->
                    viewModel.selectStatusFilter(status)
                    showSortFilterSheet = false
                }
            )
        }
    }

    // Single-Material Atomic Transaction Dialog
    val pendingAction = activeMaterialAction
    if (pendingAction != null) {
        MaterialInlineActionDialog(
            material = pendingAction.first,
            transactionType = pendingAction.second,
            isSubmitting = uiState.isSubmitting,
            onDismiss = { activeMaterialAction = null },
            onConfirm = { qty, targetLocation, notes ->
                viewModel.executeStockMovement(
                    partNumber = pendingAction.first.partNumber,
                    type = pendingAction.second,
                    qty = qty,
                    targetLocation = targetLocation,
                    notes = notes
                )
                activeMaterialAction = null
            }
        )
    }

    // Register New SKU Dialog
    if (showRegisterDialog) {
        RegisterMaterialDialog(
            defaultProductionLine = uiState.selectedLine
                ?: uiState.availableLines.firstOrNull()
                ?: "Volvo LFT",
            isSubmitting = uiState.isSubmitting,
            onDismiss = { showRegisterDialog = false },
            onConfirm = { partNumber, name, qty, threshold, location, line ->
                viewModel.registerMaterial(
                    partNumber = partNumber,
                    name = name,
                    initialQty = qty,
                    minThreshold = threshold,
                    location = location,
                    productionLine = line
                )
                showRegisterDialog = false
            }
        )
    }

    // Bulk Transfer Dialog
    if (showBulkTransferDialog && selectedMaterials.isNotEmpty()) {
        BulkTransferDialog(
            selectedCount = selectedMaterials.size,
            isSubmitting = uiState.isSubmitting,
            onDismiss = { showBulkTransferDialog = false },
            onConfirm = { targetLocation, notes ->
                viewModel.executeBulkTransfer(selectedMaterials, targetLocation, notes)
                showBulkTransferDialog = false
            }
        )
    }

    // Bulk Issue Dialog
    if (showBulkIssueDialog && selectedMaterials.isNotEmpty()) {
        BulkIssueDialog(
            selectedMaterials = selectedMaterials,
            isSubmitting = uiState.isSubmitting,
            onDismiss = { showBulkIssueDialog = false },
            onConfirm = { qtyPerItem, notes ->
                viewModel.executeBulkIssue(selectedMaterials, qtyPerItem, notes)
                showBulkIssueDialog = false
            }
        )
    }
}

@Composable
private fun RecentScansDropdownCard(
    recentScans: List<String>,
    onSelectPart: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val wmsColors = MaterialTheme.wmsColors
    Surface(
        color = wmsColors.surfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        shadowElevation = 8.dp,
        modifier = modifier.testTag("recent_scans_dropdown")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.recent_scans_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(
                color = wmsColors.outline.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            recentScans.forEach { partNumber ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectPart(partNumber) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("recent_scan_item_$partNumber")
                ) {
                    Text(
                        text = partNumber,
                        style = WmsMonoTypography.partNumber.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SortAndFilterSheetContent(
    selectedSortOption: MaterialSortOption,
    selectedStatusFilter: MaterialStatus?,
    onSelectSort: (MaterialSortOption) -> Unit,
    onSelectStatus: (MaterialStatus?) -> Unit
) {
    val wmsColors = MaterialTheme.wmsColors
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Text(
            text = stringResource(R.string.sort_sheet_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        val sortItems = listOf(
            MaterialSortOption.PART_NUMBER_ASC to R.string.sort_part_asc,
            MaterialSortOption.LOWEST_STOCK_FIRST to R.string.sort_lowest_stock,
            MaterialSortOption.LOCATION_ASC to R.string.sort_location_asc
        )

        sortItems.forEach { (option, labelRes) ->
            val isSelected = selectedSortOption == option
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else wmsColors.surfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else wmsColors.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable { onSelectSort(option) }
                    .testTag("sort_option_${option.name.lowercase()}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectSort(option) }
                    )
                }
            }
        }

        HorizontalDivider(color = wmsColors.outline.copy(alpha = 0.6f))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedStatusFilter == null,
                    onClick = { onSelectStatus(null) },
                    label = { Text(stringResource(R.string.filter_all)) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
            }
            items(MaterialStatus.entries) { status ->
                val (labelRes, dotColor) = when (status) {
                    MaterialStatus.IN_STOCK -> R.string.status_in_stock to wmsColors.statusInStock
                    MaterialStatus.LOW_STOCK -> R.string.status_low_stock to wmsColors.statusLowStock
                    MaterialStatus.OUT_OF_STOCK -> R.string.status_out_of_stock to wmsColors.statusOutOfStock
                }
                FilterChip(
                    selected = selectedStatusFilter == status,
                    onClick = { onSelectStatus(if (selectedStatusFilter == status) null else status) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("status_filter_${status.name.lowercase()}")
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaterialCard(
    material: Material,
    canTransact: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onCardClick: () -> Unit,
    onCardLongClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onReceiveClick: () -> Unit,
    onIssueClick: () -> Unit,
    onTransferClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wmsColors = MaterialTheme.wmsColors
    val status = material.statusEnum()
    val (statusColor, statusBg, statusLabelRes) = when (status) {
        MaterialStatus.IN_STOCK -> Triple(wmsColors.statusInStock, wmsColors.receiveGreenContainer, R.string.status_in_stock)
        MaterialStatus.LOW_STOCK -> Triple(wmsColors.statusLowStock, wmsColors.transferAmberContainer, R.string.status_low_stock)
        MaterialStatus.OUT_OF_STOCK -> Triple(wmsColors.statusOutOfStock, wmsColors.issueRedContainer, R.string.status_out_of_stock)
    }

    // Subtle amber/red border on LOW_STOCK / OUT_OF_STOCK cards so workers spot them immediately
    val cardBorder = when {
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        status == MaterialStatus.OUT_OF_STOCK -> BorderStroke(1.5.dp, wmsColors.statusOutOfStock.copy(alpha = 0.78f))
        status == MaterialStatus.LOW_STOCK -> BorderStroke(1.5.dp, wmsColors.statusLowStock.copy(alpha = 0.72f))
        else -> BorderStroke(1.dp, wmsColors.outline)
    }

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        status == MaterialStatus.LOW_STOCK -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = cardBorder,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = onCardLongClick
            )
            .testTag("material_card_${material.partNumber}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            // Row 1: Checkbox (if Select mode) + LARGEST Monospaced Part Number + Location Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelection() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("material_checkbox_${material.partNumber}")
                        )
                    }
                    // Part Number MUST be the LARGEST text on the card in Monospaced font ('1' vs '7' clarity)
                    Text(
                        text = material.partNumber,
                        style = WmsMonoTypography.partNumber.copy(
                            fontFamily = RobotoMonoFontFamily,
                            fontSize = 25.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("material_part_number_${material.partNumber}")
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = wmsColors.surfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("material_location_${material.partNumber}")
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
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Row 2: Material Name & Production Line + Color-coded Dot/Text Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = material.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = material.productionLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f)),
                    modifier = Modifier.testTag("material_status_${material.partNumber}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = stringResource(statusLabelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Row 3: Available Quantity (Monospaced, kept clearly smaller than the 25.sp Part Number)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = material.availableQty.toString(),
                    style = WmsMonoTypography.quantityMedium.copy(
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = statusColor,
                    modifier = Modifier.testTag("material_qty_${material.partNumber}")
                )
                Text(
                    text = material.unit,
                    style = WmsMonoTypography.location,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            HorizontalDivider(color = wmsColors.outline.copy(alpha = 0.6f))

            // Row 4: Three Colored Inline Action Buttons: Green (+ Receive), Red (- Issue), Amber (Transfer)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onReceiveClick,
                    enabled = canTransact && !isSelectionMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = wmsColors.receiveGreen,
                        contentColor = wmsColors.onReceiveGreen
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("btn_receive_${material.partNumber}")
                ) {
                    Text(
                        text = stringResource(R.string.inventory_btn_receive),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onIssueClick,
                    enabled = canTransact && !isSelectionMode && material.availableQty > 0L,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = wmsColors.issueRed,
                        contentColor = wmsColors.onIssueRed
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("btn_issue_${material.partNumber}")
                ) {
                    Text(
                        text = stringResource(R.string.inventory_btn_issue),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onTransferClick,
                    enabled = canTransact && !isSelectionMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = wmsColors.transferAmber,
                        contentColor = wmsColors.onTransferAmber
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("btn_transfer_${material.partNumber}")
                ) {
                    Text(
                        text = stringResource(R.string.inventory_btn_transfer),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkActionsBar(
    selectedCount: Int,
    canTransact: Boolean,
    isSubmitting: Boolean,
    onBulkTransferClick: () -> Unit,
    onBulkIssueClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wmsColors = MaterialTheme.wmsColors
    Surface(
        color = wmsColors.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("bulk_actions_bar")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.selected_count_label, selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bulk_clear_selection")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.dialog_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onBulkTransferClick,
                    enabled = canTransact && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = wmsColors.transferAmber,
                        contentColor = wmsColors.onTransferAmber
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("bulk_transfer_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.bulk_transfer),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onBulkIssueClick,
                    enabled = canTransact && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = wmsColors.issueRed,
                        contentColor = wmsColors.onIssueRed
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("bulk_issue_button")
                ) {
                    Text(
                        text = stringResource(R.string.bulk_issue),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onExportCsvClick,
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("bulk_export_csv_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.bulk_export_csv),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMaterialsStateView(
    searchTerm: String,
    canRegister: Boolean,
    onScanBarcodeClick: () -> Unit,
    onAddItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSearchActive = searchTerm.isNotBlank()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(24.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.wmsColors.outline),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .testTag("materials_empty_state")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Filled.SearchOff else Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = if (isSearchActive) {
                        stringResource(R.string.empty_search_message, searchTerm)
                    } else {
                        stringResource(R.string.inventory_empty_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                if (!isSearchActive) {
                    Text(
                        text = stringResource(R.string.inventory_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    OutlinedButton(
                        onClick = onScanBarcodeClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("empty_state_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_scan_barcode))
                    }
                    if (canRegister) {
                        Button(
                            onClick = onAddItemClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("empty_state_add_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_add_item))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialInlineActionDialog(
    material: Material,
    transactionType: TransactionType,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (qty: Long, targetLocation: String, notes: String) -> Unit
) {
    var qtyText by remember { mutableStateOf("1") }
    var targetLocation by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val positiveQtyError = stringResource(R.string.error_positive_qty)
    val targetLocError = stringResource(R.string.error_target_location_required)
    val insufficientTemplate = stringResource(R.string.error_insufficient_stock)

    val wmsColors = MaterialTheme.wmsColors
    val (accentColor, onAccentColor, titleAction) = when (transactionType) {
        TransactionType.RECEIVE -> Triple(wmsColors.receiveGreen, wmsColors.onReceiveGreen, stringResource(R.string.tx_receive))
        TransactionType.ISSUE -> Triple(wmsColors.issueRed, wmsColors.onIssueRed, stringResource(R.string.tx_issue))
        TransactionType.TRANSFER -> Triple(wmsColors.transferAmber, wmsColors.onTransferAmber, stringResource(R.string.tx_transfer))
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$titleAction · ${material.partNumber}",
                    style = WmsMonoTypography.partNumber,
                    color = accentColor
                )
                Text(
                    text = material.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Surface(
                    color = wmsColors.surfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.inventory_current_qty),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${material.availableQty} ${material.unit}",
                                style = WmsMonoTypography.quantityMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.inventory_current_loc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = material.location,
                                style = WmsMonoTypography.location,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { input ->
                        qtyText = input.filter { it.isDigit() }
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.dialog_quantity)) },
                    textStyle = WmsMonoTypography.quantityMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inline_tx_qty_input")
                )

                if (transactionType == TransactionType.TRANSFER) {
                    OutlinedTextField(
                        value = targetLocation,
                        onValueChange = {
                            targetLocation = it.uppercase()
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.dialog_target_location)) },
                        textStyle = WmsMonoTypography.location,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inline_tx_target_location_input")
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.dialog_notes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.toLongOrNull() ?: 0L
                    when {
                        qty <= 0L -> errorMessage = positiveQtyError
                        transactionType == TransactionType.ISSUE && qty > material.availableQty ->
                            errorMessage = String.format(insufficientTemplate, qty, material.availableQty)
                        transactionType == TransactionType.TRANSFER && targetLocation.isBlank() ->
                            errorMessage = targetLocError
                        else -> onConfirm(qty, targetLocation, notes)
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = onAccentColor
                ),
                modifier = Modifier.testTag("inline_tx_confirm_button")
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

@Composable
private fun BulkTransferDialog(
    selectedCount: Int,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (targetLocation: String, notes: String) -> Unit
) {
    var targetLocation by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val targetLocError = stringResource(R.string.error_target_location_required)

    val wmsColors = MaterialTheme.wmsColors

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.bulk_transfer_title, selectedCount),
                color = wmsColors.transferAmber
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = targetLocation,
                    onValueChange = {
                        targetLocation = it.uppercase()
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.dialog_target_location)) },
                    textStyle = WmsMonoTypography.location,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bulk_transfer_location_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.dialog_notes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (targetLocation.isBlank()) {
                        errorMessage = targetLocError
                    } else {
                        onConfirm(targetLocation, notes)
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = wmsColors.transferAmber,
                    contentColor = wmsColors.onTransferAmber
                ),
                modifier = Modifier.testTag("bulk_transfer_confirm")
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

@Composable
private fun BulkIssueDialog(
    selectedMaterials: List<Material>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (qtyPerItem: Long, notes: String) -> Unit
) {
    var qtyText by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val positiveQtyError = stringResource(R.string.error_positive_qty)
    val insufficientTemplate = stringResource(R.string.error_insufficient_stock)
    val wmsColors = MaterialTheme.wmsColors

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.bulk_issue_title, selectedMaterials.size),
                color = wmsColors.issueRed
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { input ->
                        qtyText = input.filter { it.isDigit() }
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.bulk_issue_qty_label)) },
                    textStyle = WmsMonoTypography.quantityMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bulk_issue_qty_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.dialog_notes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.toLongOrNull() ?: 0L
                    val minAvailable = selectedMaterials.minOfOrNull { it.availableQty } ?: 0L
                    when {
                        qty <= 0L -> errorMessage = positiveQtyError
                        qty > minAvailable ->
                            errorMessage = String.format(insufficientTemplate, qty, minAvailable)
                        else -> onConfirm(qty, notes)
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = wmsColors.issueRed,
                    contentColor = wmsColors.onIssueRed
                ),
                modifier = Modifier.testTag("bulk_issue_confirm")
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

private fun normalizeVoiceQuery(raw: String): String {
    val compactDigits = raw.replace(" ", "").replace("-", "")
    return if (compactDigits.isNotEmpty() && compactDigits.all { it.isDigit() }) {
        compactDigits
    } else {
        raw.trim()
    }
}
