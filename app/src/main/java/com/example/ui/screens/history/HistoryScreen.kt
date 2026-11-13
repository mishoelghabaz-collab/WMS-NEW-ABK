package com.example.ui.screens.history

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.StockTransaction
import com.example.data.model.TransactionType
import com.example.data.model.UiState
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors
import com.example.ui.viewmodel.WmsAppState
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private enum class HistoryDateFilter(val labelRes: Int) {
    ALL(R.string.history_date_all),
    TODAY(R.string.history_date_today),
    LAST_7_DAYS(R.string.history_date_7d),
    LAST_30_DAYS(R.string.history_date_30d)
}

@Composable
fun HistoryScreen(
    uiState: WmsAppState,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<TransactionType?>(null) }
    var selectedDateFilter by rememberSaveable { mutableStateOf(HistoryDateFilter.ALL) }

    val allTransactions = (uiState.transactionsState as? UiState.Success)?.data.orEmpty()
    val filteredTransactions = remember(
        allTransactions,
        searchQuery,
        selectedType,
        selectedDateFilter
    ) {
        val nowMillis = System.currentTimeMillis()
        val cutoffMillis = when (selectedDateFilter) {
            HistoryDateFilter.ALL -> 0L
            HistoryDateFilter.TODAY -> {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            HistoryDateFilter.LAST_7_DAYS -> nowMillis - 7L * 24 * 60 * 60 * 1000
            HistoryDateFilter.LAST_30_DAYS -> nowMillis - 30L * 24 * 60 * 60 * 1000
        }
        val query = searchQuery.trim().lowercase()

        allTransactions.filter { tx ->
            val matchesType = selectedType == null || tx.typeEnum() == selectedType
            val txMillis = tx.timestamp?.toDate()?.time ?: nowMillis
            val matchesDate = cutoffMillis == 0L || txMillis >= cutoffMillis
            val matchesQuery = query.isEmpty() ||
                tx.partNumber.contains(query, ignoreCase = true) ||
                tx.materialName.contains(query, ignoreCase = true) ||
                tx.user.contains(query, ignoreCase = true)
            matchesType && matchesDate && matchesQuery
        }
    }

    val dateFormatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }
    val wmsColors = MaterialTheme.wmsColors

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp)
                .align(Alignment.TopCenter)
                .testTag("history_screen")
        ) {
            // Search by Part Number or Material Name
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.history_search_hint)) },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .testTag("history_search_input")
            )

            // Type Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedType == null,
                        onClick = { selectedType = null },
                        label = { Text(stringResource(R.string.filter_all)) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
                items(TransactionType.entries) { type ->
                    val (labelRes, accentColor, bgColor) = when (type) {
                        TransactionType.RECEIVE -> Triple(R.string.tx_receive, wmsColors.receiveGreen, wmsColors.receiveGreenContainer)
                        TransactionType.ISSUE -> Triple(R.string.tx_issue, wmsColors.issueRed, wmsColors.issueRedContainer)
                        TransactionType.TRANSFER -> Triple(R.string.tx_transfer, wmsColors.transferAmber, wmsColors.transferAmberContainer)
                    }
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text(stringResource(labelRes), fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = bgColor,
                            selectedLabelColor = accentColor
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_type_${type.name.lowercase()}")
                    )
                }
            }

            // Date Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(HistoryDateFilter.entries) { dateFilter ->
                    FilterChip(
                        selected = selectedDateFilter == dateFilter,
                        onClick = { selectedDateFilter = dateFilter },
                        label = { Text(stringResource(dateFilter.labelRes)) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_date_${dateFilter.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            when (val txState = uiState.transactionsState) {
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
                                text = txState.message,
                                color = wmsColors.issueRed,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (filteredTransactions.isEmpty()) {
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
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = stringResource(R.string.history_empty),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredTransactions, key = { it.id }) { tx ->
                                TransactionAuditCard(
                                    tx = tx,
                                    dateFormatter = dateFormatter
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
private fun TransactionAuditCard(
    tx: StockTransaction,
    dateFormatter: DateFormat
) {
    val wmsColors = MaterialTheme.wmsColors
    val (accentColor, containerColor, prefix) = when (tx.typeEnum()) {
        TransactionType.RECEIVE -> Triple(wmsColors.receiveGreen, wmsColors.receiveGreenContainer, "+")
        TransactionType.ISSUE -> Triple(wmsColors.issueRed, wmsColors.issueRedContainer, "-")
        TransactionType.TRANSFER -> Triple(wmsColors.transferAmber, wmsColors.transferAmberContainer, "->")
    }
    val formattedDate = remember(tx.timestamp) {
        tx.timestamp?.toDate()?.let { dateFormatter.format(it) }
            ?: dateFormatter.format(Date())
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, wmsColors.outline),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_tx_${tx.id}")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tx.partNumber,
                        style = WmsMonoTypography.partNumber.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = tx.type,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "$prefix${tx.qty}",
                    style = WmsMonoTypography.quantityMedium,
                    color = accentColor
                )
            }

            Text(
                text = tx.materialName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                val locText = if (tx.targetLocation.isNotBlank()) {
                    "${tx.location} -> ${tx.targetLocation}"
                } else {
                    tx.location
                }
                Text(
                    text = locText,
                    style = WmsMonoTypography.location,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${tx.previousQty} -> ${tx.newQty}",
                    style = WmsMonoTypography.location,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.history_operator_label, tx.user),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (tx.notes.isNotBlank()) {
                Text(
                    text = tx.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
