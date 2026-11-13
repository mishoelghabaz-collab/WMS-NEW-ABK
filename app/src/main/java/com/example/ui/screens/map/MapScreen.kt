package com.example.ui.screens.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Material
import com.example.data.model.MaterialStatus
import com.example.data.model.UiState
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors
import com.example.ui.viewmodel.WmsAppState

private data class WarehouseBinGroup(
    val location: String,
    val zone: String,
    val items: List<Material>,
    val totalUnits: Long,
    val worstStatus: MaterialStatus
)

@Composable
fun MapScreen(
    uiState: WmsAppState,
    onOpenCycleCountForPart: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedZone by rememberSaveable { mutableStateOf<String?>(null) }
    var inspectedBin by remember { mutableStateOf<WarehouseBinGroup?>(null) }
    val wmsColors = MaterialTheme.wmsColors

    val materials = (uiState.materialsState as? UiState.Success)?.data.orEmpty()
    val binGroups = remember(materials) {
        materials
            .groupBy { it.location.trim().uppercase().ifBlank { "--" } }
            .map { (location, groupItems) ->
                val zone = location.takeWhile { it.isLetter() }.ifBlank {
                    location.substringBefore("-").ifBlank { "?" }
                }
                val worst = when {
                    groupItems.any { it.statusEnum() == MaterialStatus.OUT_OF_STOCK } ->
                        MaterialStatus.OUT_OF_STOCK
                    groupItems.any { it.statusEnum() == MaterialStatus.LOW_STOCK } ->
                        MaterialStatus.LOW_STOCK
                    else -> MaterialStatus.IN_STOCK
                }
                WarehouseBinGroup(
                    location = location,
                    zone = zone,
                    items = groupItems.sortedBy { it.partNumber },
                    totalUnits = groupItems.sumOf { it.availableQty },
                    worstStatus = worst
                )
            }
            .sortedBy { it.location }
    }

    val zones = remember(binGroups, uiState.activeFactory) {
        val fromBins = binGroups.map { it.zone }
        val fromFactory = uiState.activeFactory?.zones.orEmpty()
            .map { it.trim().uppercase().takeWhile { ch -> ch.isLetter() }.ifBlank { it } }
        (fromBins + fromFactory).filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredBins = remember(binGroups, selectedZone) {
        if (selectedZone == null) binGroups
        else binGroups.filter { it.zone.equals(selectedZone, ignoreCase = true) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 840.dp -> 3
            maxWidth >= 520.dp -> 2
            else -> 1
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp)
                .align(Alignment.TopCenter)
                .testTag("map_screen")
        ) {
            // Summary Strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.map_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.map_bins_count, filteredBins.size),
                    style = WmsMonoTypography.location,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Zone Chips
            if (zones.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedZone == null,
                            onClick = { selectedZone = null },
                            label = { Text(stringResource(R.string.filter_all)) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                    items(zones, key = { it }) { zone ->
                        val isSelected = selectedZone == zone
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedZone = if (isSelected) null else zone },
                            label = {
                                Text(
                                    text = stringResource(R.string.map_zone_label, zone),
                                    style = WmsMonoTypography.location
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                }
            }

            when (val state = uiState.materialsState) {
                is UiState.Loading -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
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
                            Text(text = state.message, color = wmsColors.issueRed, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                is UiState.Success -> {
                    if (filteredBins.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Map,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.map_empty,
                                        uiState.selectedFactoryId.ifBlank { "--" }
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredBins, key = { it.location }) { bin ->
                                BinTileCard(
                                    bin = bin,
                                    onClick = { inspectedBin = bin }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val activeBin = inspectedBin
    if (activeBin != null) {
        AlertDialog(
            onDismissRequest = { inspectedBin = null },
            title = {
                Text(
                    text = activeBin.location,
                    style = WmsMonoTypography.partNumber,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeBin.items.forEach { material ->
                        val color = when (material.statusEnum()) {
                            MaterialStatus.IN_STOCK -> wmsColors.statusInStock
                            MaterialStatus.LOW_STOCK -> wmsColors.statusLowStock
                            MaterialStatus.OUT_OF_STOCK -> wmsColors.statusOutOfStock
                        }
                        Surface(
                            onClick = {
                                inspectedBin = null
                                onOpenCycleCountForPart(material.partNumber)
                            },
                            color = wmsColors.surfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, wmsColors.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = material.partNumber,
                                        style = WmsMonoTypography.partNumber,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = material.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${material.availableQty} ${material.unit}",
                                    style = WmsMonoTypography.quantityMedium,
                                    color = color
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedBin = null }) {
                    Text(stringResource(R.string.select_mode_done))
                }
            }
        )
    }
}

@Composable
private fun BinTileCard(
    bin: WarehouseBinGroup,
    onClick: () -> Unit
) {
    val wmsColors = MaterialTheme.wmsColors
    val statusColor = when (bin.worstStatus) {
        MaterialStatus.IN_STOCK -> wmsColors.statusInStock
        MaterialStatus.LOW_STOCK -> wmsColors.statusLowStock
        MaterialStatus.OUT_OF_STOCK -> wmsColors.statusOutOfStock
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("map_bin_${bin.location}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = bin.location,
                        style = WmsMonoTypography.partNumber,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }

            HorizontalDivider(color = wmsColors.outline.copy(alpha = 0.5f))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.map_skus_count, bin.items.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.map_units_count, bin.totalUnits),
                    style = WmsMonoTypography.location,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            Text(
                text = bin.items.joinToString(" · ") { it.partNumber },
                style = WmsMonoTypography.location,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
