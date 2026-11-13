package com.example.ui.screens.admin

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
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DiscrepancyRecord
import com.example.data.model.Factory
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.RoleBadge
import com.example.ui.screens.dashboard.CreateFactoryDialog
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors
import com.example.ui.viewmodel.AdminUiState
import com.example.ui.viewmodel.WmsAppState
import java.text.DateFormat
import java.util.Date

@Composable
fun AdminScreen(
    appState: WmsAppState,
    adminState: AdminUiState,
    onOpenOcrImport: () -> Unit,
    onOpenCycleCount: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onCreateFactory: (code: String, name: String, linesCsv: String, zonesCsv: String) -> Unit,
    onUpdateUserAccess: (uid: String, role: UserRole, factoryIdsCsv: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val wmsColors = MaterialTheme.wmsColors
    var showCreatePlantDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<UserProfile?>(null) }

    val canTransact = appState.activeRole.canTransact()
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp)
                .align(Alignment.TopCenter)
                .testTag("admin_screen")
        ) {
            // 0. Theme Mode Preference Card (Dark / Light / System)
            item {
                ThemePreferenceCard(
                    selectedMode = appState.themeMode,
                    onSelectMode = onSelectThemeMode
                )
            }

            // 1. Warehouse Operations Shortcuts (OCR Import + Fast Cycle Count)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.admin_operations_tools),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OperationLaunchCard(
                            title = stringResource(R.string.action_ocr_import),
                            subtitle = stringResource(R.string.ocr_screen_title),
                            icon = Icons.Filled.DocumentScanner,
                            accentColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            enabled = canTransact && appState.selectedFactoryId.isNotBlank(),
                            onClick = onOpenOcrImport,
                            testTag = "admin_open_ocr_import",
                            modifier = Modifier.weight(1f)
                        )
                        OperationLaunchCard(
                            title = stringResource(R.string.action_cycle_count),
                            subtitle = stringResource(R.string.cc_screen_title),
                            icon = Icons.Filled.FactCheck,
                            accentColor = wmsColors.receiveGreen,
                            containerColor = wmsColors.receiveGreenContainer,
                            enabled = canTransact && appState.selectedFactoryId.isNotBlank(),
                            onClick = onOpenCycleCount,
                            testTag = "admin_open_cycle_count",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. Cycle Count Discrepancies Feed (Firestore discrepancies collection)
            item {
                Text(
                    text = stringResource(R.string.admin_discrepancies_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            when (val discState = adminState.discrepanciesState) {
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
                        Surface(
                            color = wmsColors.issueRedContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, wmsColors.issueRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = discState.message,
                                color = wmsColors.issueRed,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (discState.data.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, wmsColors.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.admin_no_discrepancies),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(discState.data.take(15), key = { it.id }) { disc ->
                            DiscrepancyCard(record = disc, dateFormatter = dateFormatter)
                        }
                    }
                }
            }

            // 3. Configured Plants Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.admin_plants_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (adminState.isAdmin) {
                        FilledTonalButton(
                            onClick = { showCreatePlantDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("admin_add_plant_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.dashboard_add_factory))
                        }
                    }
                }
            }

            items(appState.factories, key = { it.code }) { factory ->
                PlantSummaryCard(factory = factory)
            }

            // 4. User Roles & Plant Access Section
            item {
                Text(
                    text = stringResource(R.string.admin_users_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (!adminState.isAdmin) {
                item {
                    Surface(
                        color = wmsColors.transferAmberContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, wmsColors.transferAmber.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.admin_readonly_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = wmsColors.transferAmber,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                val currentProfile = appState.userProfile
                if (currentProfile != null) {
                    item {
                        UserAccessCard(
                            user = currentProfile,
                            canEdit = false,
                            onEditClick = {}
                        )
                    }
                }
            } else {
                when (val usersState = adminState.usersState) {
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
                            Text(text = usersState.message, color = wmsColors.issueRed)
                        }
                    }
                    is UiState.Success -> {
                        items(usersState.data, key = { it.uid }) { user ->
                            UserAccessCard(
                                user = user,
                                canEdit = true,
                                onEditClick = { editingUser = user }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlantDialog) {
        CreateFactoryDialog(
            isSubmitting = appState.isSubmittingAction,
            onDismiss = { showCreatePlantDialog = false },
            onConfirm = { code, name, lines, zones ->
                onCreateFactory(code, name, lines, zones)
                showCreatePlantDialog = false
            }
        )
    }

    val targetUser = editingUser
    if (targetUser != null) {
        EditUserAccessDialog(
            user = targetUser,
            isSubmitting = adminState.isSubmitting,
            onDismiss = { editingUser = null },
            onConfirm = { role, factoryIdsCsv ->
                onUpdateUserAccess(targetUser.uid, role, factoryIdsCsv)
                editingUser = null
            }
        )
    }
}

@Composable
private fun OperationLaunchCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    containerColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (enabled) 0.65f else 0.25f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ThemePreferenceCard(
    selectedMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit
) {
    val wmsColors = MaterialTheme.wmsColors
    val modes = listOf(
        Triple(ThemeMode.DARK, R.string.theme_dark, Icons.Filled.DarkMode),
        Triple(ThemeMode.LIGHT, R.string.theme_light, Icons.Filled.LightMode),
        Triple(ThemeMode.SYSTEM, R.string.theme_system, Icons.Filled.BrightnessAuto)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, wmsColors.outline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_theme_selector_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = stringResource(R.string.theme_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                modes.forEach { (mode, labelRes, icon) ->
                    val selected = selectedMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMode(mode) },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("admin_theme_${mode.code}")
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscrepancyCard(
    record: DiscrepancyRecord,
    dateFormatter: DateFormat
) {
    val wmsColors = MaterialTheme.wmsColors
    val variancePrefix = if (record.variance > 0) "+" else ""
    val formattedDate = remember(record.timestamp) {
        record.timestamp?.toDate()?.let { dateFormatter.format(it) } ?: dateFormatter.format(Date())
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, wmsColors.issueRed.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discrepancy_card_${record.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = wmsColors.issueRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = record.partNumber,
                        style = WmsMonoTypography.partNumber,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "$variancePrefix${record.variance}",
                    style = WmsMonoTypography.quantityMedium,
                    color = wmsColors.issueRed
                )
            }

            Text(
                text = "${record.materialName} · ${record.location}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${record.systemQty} -> ${record.actualQty}",
                    style = WmsMonoTypography.location,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${record.reportedBy} · $formattedDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (record.notes.isNotBlank()) {
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PlantSummaryCard(factory: Factory) {
    val wmsColors = MaterialTheme.wmsColors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, wmsColors.outline),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Factory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${factory.code} · ${factory.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = factory.productionLines.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UserAccessCard(
    user: UserProfile,
    canEdit: Boolean,
    onEditClick: () -> Unit
) {
    val wmsColors = MaterialTheme.wmsColors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, wmsColors.outline),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_user_${user.uid}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = user.displayName.ifBlank { user.email },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.factoryIds.joinToString(", ").ifBlank { "--" },
                        style = WmsMonoTypography.location,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RoleBadge(role = user.roleEnum())
                if (canEdit) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_edit_user_${user.uid}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.admin_edit_user)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditUserAccessDialog(
    user: UserProfile,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (role: UserRole, factoryIdsCsv: String) -> Unit
) {
    var selectedRole by remember(user) { mutableStateOf(user.roleEnum()) }
    var factoryIdsCsv by remember(user) { mutableStateOf(user.factoryIds.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = user.displayName.ifBlank { user.email },
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(UserRole.entries) { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                }
                OutlinedTextField(
                    value = factoryIdsCsv,
                    onValueChange = { factoryIdsCsv = it.uppercase() },
                    label = { Text(stringResource(R.string.admin_assigned_plants)) },
                    textStyle = WmsMonoTypography.location,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedRole, factoryIdsCsv) },
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
