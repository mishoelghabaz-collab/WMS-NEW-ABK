package com.example.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Factory
import com.example.data.model.StockTransaction
import com.example.data.model.TransactionType
import com.example.data.model.UiState
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors
import com.example.ui.viewmodel.DashboardMetrics
import com.example.ui.viewmodel.WmsAppState
import java.text.NumberFormat

@Composable
fun DashboardScreen(
    uiState: WmsAppState,
    onSelectFactory: (String) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenCycleCount: () -> Unit,
    onOpenOcrImport: () -> Unit,
    onCreateFactory: (code: String, name: String, linesCsv: String, zonesCsv: String) -> Unit,
    onRegisterMaterial: (
        partNumber: String,
        name: String,
        initialQty: Long,
        minThreshold: Long,
        location: String,
        productionLine: String
    ) -> Unit,
    onExecuteStockMovement: (
        partNumber: String,
        type: TransactionType,
        qty: Long,
        targetLocation: String,
        notes: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreatePlantDialog by remember { mutableStateOf(false) }
    var activeTxDialogType by remember { mutableStateOf<TransactionType?>(null) }
    var showRegisterSkuDialog by remember { mutableStateOf(false) }

    val canTransact = uiState.activeRole.canTransact()
    val canManageMaterials = uiState.activeRole.canManageMaterials()
    val isAdmin = uiState.activeRole.isAdmin()

    val wmsColors = MaterialTheme.wmsColors

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 680.dp

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp)
                .align(Alignment.TopCenter)
                .testTag("dashboard_screen")
        ) {
            // 1. Top Factory Selector Card
            item {
                FactorySelectorSection(
                    factories = uiState.factories,
                    selectedFactoryId = uiState.selectedFactoryId,
                    isAdmin = isAdmin,
                    onSelectFactory = onSelectFactory,
                    onAddFactoryClick = { showCreatePlantDialog = true }
                )
            }

            // Read-only notice for VIEWER role
            if (!canTransact && !uiState.isProfileLoading) {
                item {
                    Surface(
                        color = wmsColors.transferAmberContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, wmsColors.transferAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_viewer_readonly),
                            style = MaterialTheme.typography.bodyMedium,
                            color = wmsColors.transferAmber,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // 2. 4 Metric Cards (Total Stock, SKUs, Critical Stock, Warning)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_metrics_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (uiState.materialsState is UiState.Loading && uiState.selectedFactoryId.isNotBlank()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (uiState.materialsState is UiState.Error) {
                        Surface(
                            color = wmsColors.issueRedContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, wmsColors.issueRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.materialsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = wmsColors.issueRed,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        MetricsGrid(
                            metrics = uiState.metrics,
                            isWide = isWide
                        )
                    }
                }
            }

            // 3. Grid of Quick Action Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_quick_actions),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    QuickActionsGrid(
                        isWide = isWide,
                        canTransact = canTransact && uiState.selectedFactoryId.isNotBlank(),
                        canRegister = canManageMaterials && uiState.selectedFactoryId.isNotBlank(),
                        onScanClick = onOpenScanner,
                        onReceiveClick = {
                            if (uiState.metrics.uniqueSkus == 0 && canManageMaterials) {
                                showRegisterSkuDialog = true
                            } else {
                                activeTxDialogType = TransactionType.RECEIVE
                            }
                        },
                        onIssueClick = { activeTxDialogType = TransactionType.ISSUE },
                        onTransferClick = { activeTxDialogType = TransactionType.TRANSFER },
                        onCycleCountClick = onOpenCycleCount,
                        onOcrImportClick = onOpenOcrImport
                    )
                }
            }

            // 4. Live Recent Movements from Firestore
            item {
                Text(
                    text = stringResource(R.string.dashboard_recent_transactions),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            when (val txState = uiState.transactionsState) {
                is UiState.Loading -> {
                    if (uiState.selectedFactoryId.isNotBlank()) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = txState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = wmsColors.issueRed
                        )
                    }
                }
                is UiState.Success -> {
                    val recent = txState.data.take(6)
                    if (recent.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, wmsColors.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_no_transactions),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(recent, key = { it.id }) { tx ->
                            RecentTransactionCard(transaction = tx)
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlantDialog) {
        CreateFactoryDialog(
            isSubmitting = uiState.isSubmittingAction,
            onDismiss = { showCreatePlantDialog = false },
            onConfirm = { code, name, lines, zones ->
                onCreateFactory(code, name, lines, zones)
                showCreatePlantDialog = false
            }
        )
    }

    val txType = activeTxDialogType
    if (txType != null) {
        QuickStockMovementDialog(
            initialType = txType,
            canRegisterMaterial = canManageMaterials,
            isSubmitting = uiState.isSubmittingAction,
            onDismiss = { activeTxDialogType = null },
            onSwitchToRegisterSku = {
                activeTxDialogType = null
                showRegisterSkuDialog = true
            },
            onConfirm = { partNumber, type, qty, targetLocation, notes ->
                onExecuteStockMovement(partNumber, type, qty, targetLocation, notes)
                activeTxDialogType = null
            }
        )
    }

    if (showRegisterSkuDialog) {
        RegisterMaterialDialog(
            defaultProductionLine = uiState.activeFactory?.productionLines?.firstOrNull().orEmpty(),
            isSubmitting = uiState.isSubmittingAction,
            onDismiss = { showRegisterSkuDialog = false },
            onConfirm = { partNumber, name, qty, threshold, location, line ->
                onRegisterMaterial(partNumber, name, qty, threshold, location, line)
                showRegisterSkuDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FactorySelectorSection(
    factories: List<Factory>,
    selectedFactoryId: String,
    isAdmin: Boolean,
    onSelectFactory: (String) -> Unit,
    onAddFactoryClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val activeFactory = factories.firstOrNull { it.code == selectedFactoryId }
    val wmsColors = MaterialTheme.wmsColors

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, wmsColors.outline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_factory_selector_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Factory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.dashboard_factory_selector_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isAdmin) {
                    FilledTonalButton(
                        onClick = onAddFactoryClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_add_factory_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.dashboard_add_factory))
                    }
                }
            }

            if (factories.isEmpty()) {
                Surface(
                    color = wmsColors.surfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_no_plants_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.dashboard_no_plants_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = if (activeFactory != null) {
                            "${activeFactory.code} — ${activeFactory.name}"
                        } else {
                            selectedFactoryId
                        },
                        onValueChange = {},
                        readOnly = true,
                        textStyle = WmsMonoTypography.location,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("dashboard_factory_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        factories.forEach { factory ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${factory.code} — ${factory.name}",
                                        style = WmsMonoTypography.location
                                    )
                                },
                                onClick = {
                                    onSelectFactory(factory.code)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Quick plant chips for one-tap switching
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(factories, key = { it.code }) { factory ->
                        val isSelected = factory.code == selectedFactoryId
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectFactory(factory.code) },
                            label = {
                                Text(
                                    text = factory.code,
                                    style = WmsMonoTypography.location,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("factory_chip_${factory.code}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricsGrid(
    metrics: DashboardMetrics,
    isWide: Boolean
) {
    val formatter = remember { NumberFormat.getIntegerInstance() }
    val wmsColors = MaterialTheme.wmsColors
    val cards = listOf(
        MetricCardData(
            title = stringResource(R.string.metric_total_stock),
            subtitle = stringResource(R.string.metric_total_stock_sub),
            value = formatter.format(metrics.totalStock),
            icon = Icons.Filled.Inventory,
            accentColor = wmsColors.receiveGreen,
            containerColor = wmsColors.receiveGreenContainer,
            testTag = "metric_card_total_stock"
        ),
        MetricCardData(
            title = stringResource(R.string.metric_skus),
            subtitle = stringResource(R.string.metric_skus_sub),
            value = formatter.format(metrics.uniqueSkus),
            icon = Icons.Filled.Category,
            accentColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            testTag = "metric_card_skus"
        ),
        MetricCardData(
            title = stringResource(R.string.metric_critical_stock),
            subtitle = stringResource(R.string.metric_critical_stock_sub),
            value = formatter.format(metrics.criticalStockCount),
            icon = Icons.Filled.ErrorOutline,
            accentColor = wmsColors.issueRed,
            containerColor = wmsColors.issueRedContainer,
            testTag = "metric_card_critical_stock"
        ),
        MetricCardData(
            title = stringResource(R.string.metric_warning),
            subtitle = stringResource(R.string.metric_warning_sub),
            value = formatter.format(metrics.warningCount),
            icon = Icons.Filled.WarningAmber,
            accentColor = wmsColors.transferAmber,
            containerColor = wmsColors.transferAmberContainer,
            testTag = "metric_card_warning"
        )
    )

    if (isWide) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            cards.forEach { card ->
                MetricCard(data = card, modifier = Modifier.weight(1f))
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricCard(data = cards[0], modifier = Modifier.weight(1f))
                MetricCard(data = cards[1], modifier = Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricCard(data = cards[2], modifier = Modifier.weight(1f))
                MetricCard(data = cards[3], modifier = Modifier.weight(1f))
            }
        }
    }
}

private data class MetricCardData(
    val title: String,
    val subtitle: String,
    val value: String,
    val icon: ImageVector,
    val accentColor: Color,
    val containerColor: Color,
    val testTag: String
)

@Composable
private fun MetricCard(
    data: MetricCardData,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, data.accentColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.testTag(data.testTag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = data.containerColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = data.icon,
                        contentDescription = null,
                        tint = data.accentColor,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
            }

            Text(
                text = data.value,
                style = WmsMonoTypography.quantityLarge,
                color = data.accentColor,
                maxLines = 1
            )

            Text(
                text = data.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionsGrid(
    isWide: Boolean,
    canTransact: Boolean,
    canRegister: Boolean,
    onScanClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onIssueClick: () -> Unit,
    onTransferClick: () -> Unit,
    onCycleCountClick: () -> Unit,
    onOcrImportClick: () -> Unit
) {
    val wmsColors = MaterialTheme.wmsColors
    val actions = listOf(
        QuickActionItem(
            label = stringResource(R.string.action_scan),
            icon = Icons.Filled.QrCodeScanner,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            enabled = true,
            onClick = onScanClick,
            testTag = "quick_action_scan"
        ),
        QuickActionItem(
            label = stringResource(R.string.action_receive),
            icon = Icons.Filled.AddBox,
            containerColor = wmsColors.receiveGreenContainer,
            contentColor = wmsColors.receiveGreen,
            enabled = canTransact || canRegister,
            onClick = onReceiveClick,
            testTag = "quick_action_receive"
        ),
        QuickActionItem(
            label = stringResource(R.string.action_issue),
            icon = Icons.Filled.IndeterminateCheckBox,
            containerColor = wmsColors.issueRedContainer,
            contentColor = wmsColors.issueRed,
            enabled = canTransact,
            onClick = onIssueClick,
            testTag = "quick_action_issue"
        ),
        QuickActionItem(
            label = stringResource(R.string.action_transfer),
            icon = Icons.Filled.SwapHoriz,
            containerColor = wmsColors.transferAmberContainer,
            contentColor = wmsColors.transferAmber,
            enabled = canTransact,
            onClick = onTransferClick,
            testTag = "quick_action_transfer"
        ),
        QuickActionItem(
            label = stringResource(R.string.action_cycle_count),
            icon = Icons.Filled.FactCheck,
            containerColor = wmsColors.receiveGreenContainer,
            contentColor = wmsColors.receiveGreen,
            enabled = canTransact,
            onClick = onCycleCountClick,
            testTag = "quick_action_cycle_count"
        ),
        QuickActionItem(
            label = stringResource(R.string.action_ocr_import),
            icon = Icons.Filled.DocumentScanner,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            enabled = canTransact,
            onClick = onOcrImportClick,
            testTag = "quick_action_ocr_import"
        )
    )

    val columns = if (isWide) 3 else 2
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.chunked(columns).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { action ->
                    QuickActionButton(item = action, modifier = Modifier.weight(1f))
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class QuickActionItem(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val testTag: String
)

@Composable
private fun QuickActionButton(
    item: QuickActionItem,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = item.onClick,
        enabled = item.enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = item.containerColor,
            contentColor = item.contentColor
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.enabled) item.contentColor.copy(alpha = 0.55f) else MaterialTheme.wmsColors.outline
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        modifier = modifier
            .height(64.dp)
            .minimumInteractiveComponentSize()
            .testTag(item.testTag)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentTransactionCard(
    transaction: StockTransaction,
    modifier: Modifier = Modifier
) {
    val wmsColors = MaterialTheme.wmsColors
    val (accentColor, prefix) = when (transaction.typeEnum()) {
        TransactionType.RECEIVE -> wmsColors.receiveGreen to "+"
        TransactionType.ISSUE -> wmsColors.issueRed to "-"
        TransactionType.TRANSFER -> wmsColors.transferAmber to "->"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, wmsColors.outline),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = transaction.partNumber,
                        style = WmsMonoTypography.partNumber,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = transaction.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = transaction.materialName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val locationDisplay = if (transaction.targetLocation.isNotBlank()) {
                    "${transaction.location} -> ${transaction.targetLocation}"
                } else {
                    transaction.location
                }
                Text(
                    text = locationDisplay,
                    style = WmsMonoTypography.location,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix${transaction.qty}",
                    style = WmsMonoTypography.quantityMedium,
                    color = accentColor
                )
                Text(
                    text = "${transaction.previousQty} -> ${transaction.newQty}",
                    style = WmsMonoTypography.location,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
