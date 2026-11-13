package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.Factory
import com.example.data.model.Material
import com.example.data.model.MaterialStatus
import com.example.data.model.TransactionType
import com.example.data.model.UiState
import com.example.data.preferences.WmsPreferencesRepository
import com.example.data.repository.WmsRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MaterialSortOption {
    PART_NUMBER_ASC,
    LOWEST_STOCK_FIRST,
    LOCATION_ASC
}

data class MaterialsUiState(
    val activeFactoryId: String = "",
    val debouncedQuery: String = "",
    val selectedLine: String? = null,
    val selectedStatusFilter: MaterialStatus? = null,
    val sortOption: MaterialSortOption = MaterialSortOption.PART_NUMBER_ASC,
    val recentScans: List<String> = emptyList(),
    val isSelectionMode: Boolean = false,
    val selectedPartNumbers: Set<String> = emptySet(),
    val availableLines: List<String> = DEFAULT_PRODUCTION_LINES,
    val materialsState: UiState<List<Material>> = UiState.Loading,
    val isSubmitting: Boolean = false,
    val actionFeedback: ActionFeedback? = null
) {
    companion object {
        val DEFAULT_PRODUCTION_LINES = listOf("Volvo LFT", "CDPO", "Nissan")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MaterialsViewModel(
    private val repository: WmsRepository,
    private val preferences: WmsPreferencesRepository,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val currentUserName: String
) : ViewModel() {

    private val _activeFactoryId = MutableStateFlow("")
    private val _factoryLines = MutableStateFlow<List<String>>(emptyList())
    private val _debouncedQuery = MutableStateFlow("")
    private val _selectedLine = MutableStateFlow<String?>(null)
    private val _selectedStatusFilter = MutableStateFlow<MaterialStatus?>(null)
    private val _sortOption = MutableStateFlow(MaterialSortOption.PART_NUMBER_ASC)
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedPartNumbers = MutableStateFlow<Set<String>>(emptySet())
    private val _isSubmitting = MutableStateFlow(false)
    private val _actionFeedback = MutableStateFlow<ActionFeedback?>(null)

    private val rawMaterialsFlow: StateFlow<UiState<List<Material>>> =
        _activeFactoryId
            .flatMapLatest { factoryId ->
                if (factoryId.isBlank()) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    repository.observeMaterials(factoryId)
                        .map<List<Material>, UiState<List<Material>>> { UiState.Success(it) }
                        .catchFirestoreError()
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val recentScansFlow: StateFlow<List<String>> =
        preferences.recentScans.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    private val filterControlsFlow = combine(
        _debouncedQuery,
        _selectedLine,
        _selectedStatusFilter,
        _sortOption,
        _factoryLines
    ) { query, line, statusFilter, sortOption, factoryLines ->
        FilterControls(
            query = query,
            selectedLine = line,
            selectedStatusFilter = statusFilter,
            sortOption = sortOption,
            factoryLines = factoryLines
        )
    }

    private val selectionControlsFlow = combine(
        _isSelectionMode,
        _selectedPartNumbers,
        recentScansFlow,
        _isSubmitting,
        _actionFeedback
    ) { isSelectionMode, selectedParts, recentScans, isSubmitting, feedback ->
        SelectionControls(
            isSelectionMode = isSelectionMode,
            selectedPartNumbers = selectedParts,
            recentScans = recentScans,
            isSubmitting = isSubmitting,
            actionFeedback = feedback
        )
    }

    val uiState: StateFlow<MaterialsUiState> = combine(
        _activeFactoryId,
        rawMaterialsFlow,
        filterControlsFlow,
        selectionControlsFlow
    ) { factoryId, rawState, filters, selection ->
        val rawList = (rawState as? UiState.Success)?.data.orEmpty()
        val availableLines = (MaterialsUiState.DEFAULT_PRODUCTION_LINES +
            filters.factoryLines +
            rawList.map { it.productionLine })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val filteredState = when (rawState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> rawState
            is UiState.Success -> {
                val filteredAndSorted = applyFilterAndSort(
                    materials = rawState.data,
                    query = filters.query,
                    selectedLine = filters.selectedLine,
                    selectedStatus = filters.selectedStatusFilter,
                    sortOption = filters.sortOption
                )
                UiState.Success(filteredAndSorted)
            }
        }

        val validPartNumbers = rawList.map { it.partNumber }.toSet()
        val prunedSelection = selection.selectedPartNumbers.intersect(validPartNumbers)

        MaterialsUiState(
            activeFactoryId = factoryId,
            debouncedQuery = filters.query,
            selectedLine = filters.selectedLine,
            selectedStatusFilter = filters.selectedStatusFilter,
            sortOption = filters.sortOption,
            recentScans = selection.recentScans,
            isSelectionMode = selection.isSelectionMode,
            selectedPartNumbers = prunedSelection,
            availableLines = availableLines,
            materialsState = filteredState,
            isSubmitting = selection.isSubmitting,
            actionFeedback = selection.actionFeedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MaterialsUiState()
    )

    fun syncFactoryContext(factoryId: String, activeFactory: Factory?) {
        val factoryChanged = _activeFactoryId.value != factoryId
        _activeFactoryId.value = factoryId
        _factoryLines.value = activeFactory?.productionLines.orEmpty()
        if (factoryChanged) {
            _selectedPartNumbers.value = emptySet()
            _isSelectionMode.value = false
        }
    }

    fun updateDebouncedQuery(query: String) {
        _debouncedQuery.value = query.trim()
    }

    fun recordRecentScan(partNumber: String) {
        val cleaned = partNumber.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            preferences.addRecentScan(cleaned)
        }
    }

    fun selectLine(line: String?) {
        _selectedLine.value = line
    }

    fun selectStatusFilter(status: MaterialStatus?) {
        _selectedStatusFilter.value = status
    }

    fun selectSortOption(option: MaterialSortOption) {
        _sortOption.value = option
    }

    fun toggleSelectionMode() {
        val next = !_isSelectionMode.value
        _isSelectionMode.value = next
        if (!next) {
            _selectedPartNumbers.value = emptySet()
        }
    }

    fun enterSelectionModeWith(partNumber: String) {
        _isSelectionMode.value = true
        _selectedPartNumbers.value = _selectedPartNumbers.value + partNumber
    }

    fun toggleSelectMaterial(partNumber: String) {
        val current = _selectedPartNumbers.value
        _selectedPartNumbers.value = if (partNumber in current) {
            current - partNumber
        } else {
            current + partNumber
        }
    }

    fun clearSelection() {
        _selectedPartNumbers.value = emptySet()
        _isSelectionMode.value = false
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    fun postFeedback(message: String, isError: Boolean) {
        _actionFeedback.value = ActionFeedback(message = message, isError = isError)
    }

    fun registerMaterial(
        partNumber: String,
        name: String,
        initialQty: Long,
        minThreshold: Long,
        location: String,
        productionLine: String,
        unit: String = Material.DEFAULT_UNIT
    ) {
        val factoryId = _activeFactoryId.value
        if (factoryId.isBlank()) {
            _actionFeedback.value = ActionFeedback("Select or create a plant first.", isError = true)
            return
        }
        val cleanPart = partNumber.trim()
        if (!Material.isValidPartNumber(cleanPart)) {
            _actionFeedback.value = ActionFeedback("Part number must be exactly 8 digits.", isError = true)
            return
        }
        if (name.isBlank() || location.isBlank() || productionLine.isBlank()) {
            _actionFeedback.value = ActionFeedback("Name, location, and line are required.", isError = true)
            return
        }
        if (initialQty < 0L || minThreshold < 0L) {
            _actionFeedback.value = ActionFeedback("Quantities cannot be negative.", isError = true)
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            repository.createMaterial(
                Material(
                    factoryId = factoryId,
                    partNumber = cleanPart,
                    name = name.trim(),
                    availableQty = initialQty,
                    minThreshold = minThreshold,
                    location = location.trim(),
                    productionLine = productionLine.trim(),
                    unit = unit.trim().ifBlank { Material.DEFAULT_UNIT }
                )
            ).onSuccess {
                preferences.addRecentScan(cleanPart)
                _actionFeedback.value = ActionFeedback(
                    "SKU $cleanPart registered in $factoryId.",
                    isError = false
                )
            }.onFailure { error ->
                _actionFeedback.value = ActionFeedback(
                    message = error.localizedMessage ?: "Failed to register SKU",
                    isError = true
                )
            }
            _isSubmitting.value = false
        }
    }

    fun executeStockMovement(
        partNumber: String,
        type: TransactionType,
        qty: Long,
        targetLocation: String = "",
        notes: String = ""
    ) {
        val factoryId = _activeFactoryId.value
        val cleanPart = partNumber.trim()
        if (factoryId.isBlank()) {
            _actionFeedback.value = ActionFeedback("Select an active plant first.", isError = true)
            return
        }
        if (!Material.isValidPartNumber(cleanPart)) {
            _actionFeedback.value = ActionFeedback("Part number must be exactly 8 digits.", isError = true)
            return
        }
        if (qty <= 0L) {
            _actionFeedback.value = ActionFeedback("Quantity must be greater than zero.", isError = true)
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            repository.executeAtomicStockTransaction(
                factoryId = factoryId,
                partNumber = cleanPart,
                type = type,
                qty = qty,
                operatorName = operatorDisplayName(),
                targetLocation = targetLocation.trim(),
                notes = notes.trim()
            ).onSuccess { tx ->
                preferences.addRecentScan(cleanPart)
                _actionFeedback.value = ActionFeedback(
                    "${tx.type}: ${tx.partNumber} (${tx.previousQty} -> ${tx.newQty})",
                    isError = false
                )
            }.onFailure { error ->
                _actionFeedback.value = ActionFeedback(
                    message = error.localizedMessage ?: "Transaction rejected",
                    isError = true
                )
            }
            _isSubmitting.value = false
        }
    }

    fun executeBulkIssue(
        selectedMaterials: List<Material>,
        qtyPerItem: Long,
        notes: String
    ) {
        val factoryId = _activeFactoryId.value
        if (factoryId.isBlank() || selectedMaterials.isEmpty()) return
        if (qtyPerItem <= 0L) {
            _actionFeedback.value = ActionFeedback("Quantity must be greater than zero.", isError = true)
            return
        }

        val insufficient = selectedMaterials.filter { it.availableQty < qtyPerItem }
        if (insufficient.isNotEmpty()) {
            val preview = insufficient.take(3).joinToString(", ") { it.partNumber }
            _actionFeedback.value = ActionFeedback(
                message = "Insufficient stock for bulk issue ($preview).",
                isError = true
            )
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val operator = operatorDisplayName()
            var successCount = 0
            var lastError: String? = null

            for (material in selectedMaterials) {
                repository.executeAtomicStockTransaction(
                    factoryId = factoryId,
                    partNumber = material.partNumber,
                    type = TransactionType.ISSUE,
                    qty = qtyPerItem,
                    operatorName = operator,
                    notes = notes.trim().ifBlank { "Bulk issue" }
                ).onSuccess {
                    successCount++
                }.onFailure { error ->
                    lastError = error.localizedMessage
                }
            }

            if (successCount > 0) {
                clearSelection()
            }

            _actionFeedback.value = if (lastError == null) {
                ActionFeedback("Bulk ISSUE committed for $successCount SKUs.", isError = false)
            } else {
                ActionFeedback(
                    "Bulk ISSUE: $successCount/${selectedMaterials.size} succeeded. $lastError",
                    isError = successCount == 0
                )
            }
            _isSubmitting.value = false
        }
    }

    fun executeBulkTransfer(
        selectedMaterials: List<Material>,
        targetLocation: String,
        notes: String
    ) {
        val factoryId = _activeFactoryId.value
        val cleanLocation = targetLocation.trim().uppercase()
        if (factoryId.isBlank() || selectedMaterials.isEmpty()) return
        if (cleanLocation.isBlank()) {
            _actionFeedback.value = ActionFeedback("Target bin location is required.", isError = true)
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val operator = operatorDisplayName()
            var successCount = 0
            var lastError: String? = null

            for (material in selectedMaterials) {
                val transferQty = material.availableQty.coerceAtLeast(1L)
                repository.executeAtomicStockTransaction(
                    factoryId = factoryId,
                    partNumber = material.partNumber,
                    type = TransactionType.TRANSFER,
                    qty = transferQty,
                    operatorName = operator,
                    targetLocation = cleanLocation,
                    notes = notes.trim().ifBlank { "Bulk transfer to $cleanLocation" }
                ).onSuccess {
                    successCount++
                }.onFailure { error ->
                    lastError = error.localizedMessage
                }
            }

            if (successCount > 0) {
                clearSelection()
            }

            _actionFeedback.value = if (lastError == null) {
                ActionFeedback(
                    "Bulk TRANSFER to $cleanLocation committed for $successCount SKUs.",
                    isError = false
                )
            } else {
                ActionFeedback(
                    "Bulk TRANSFER: $successCount/${selectedMaterials.size} succeeded. $lastError",
                    isError = successCount == 0
                )
            }
            _isSubmitting.value = false
        }
    }

    fun buildCsvExportIntent(context: Context, selectedMaterials: List<Material>): Intent? {
        if (selectedMaterials.isEmpty()) return null
        val factoryId = _activeFactoryId.value.ifBlank { "PLANT" }
        return runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val csvFile = File(exportDir, "wms_inventory_${factoryId}_$stamp.csv")

            val content = buildString {
                appendLine("Factory,PartNumber,Name,Location,ProductionLine,AvailableQty,Unit,MinThreshold,Status")
                for (item in selectedMaterials) {
                    append(escapeCsv(item.factoryId)).append(',')
                    append(escapeCsv(item.partNumber)).append(',')
                    append(escapeCsv(item.name)).append(',')
                    append(escapeCsv(item.location)).append(',')
                    append(escapeCsv(item.productionLine)).append(',')
                    append(item.availableQty).append(',')
                    append(escapeCsv(item.unit)).append(',')
                    append(item.minThreshold).append(',')
                    appendLine(escapeCsv(item.statusEnum().name))
                }
            }
            csvFile.writeText(content, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    context.getString(R.string.csv_export_subject, factoryId)
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(shareIntent, context.getString(R.string.bulk_export_csv))
        }.onFailure { error ->
            Log.w(TAG, "CSV export failed", error)
            _actionFeedback.value = ActionFeedback(
                error.localizedMessage ?: "CSV export failed.",
                isError = true
            )
        }.getOrNull()
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun operatorDisplayName(): String =
        currentUserName.ifBlank { currentUserEmail }.ifBlank { currentUserId }

    private fun applyFilterAndSort(
        materials: List<Material>,
        query: String,
        selectedLine: String?,
        selectedStatus: MaterialStatus?,
        sortOption: MaterialSortOption
    ): List<Material> {
        val normalizedQuery = query.trim().lowercase()
        val filtered = materials.filter { item ->
            val matchesLine = selectedLine == null ||
                item.productionLine.equals(selectedLine, ignoreCase = true)
            val matchesStatus = selectedStatus == null ||
                item.statusEnum() == selectedStatus
            val matchesQuery = normalizedQuery.isEmpty() ||
                item.partNumber.contains(normalizedQuery, ignoreCase = true) ||
                item.name.contains(normalizedQuery, ignoreCase = true)
            matchesLine && matchesStatus && matchesQuery
        }

        return when (sortOption) {
            MaterialSortOption.PART_NUMBER_ASC -> filtered.sortedWith(
                compareBy<Material> { it.partNumber }.thenBy { it.name.lowercase() }
            )
            MaterialSortOption.LOWEST_STOCK_FIRST -> filtered.sortedWith(
                compareBy<Material> { it.availableQty }.thenBy { it.partNumber }
            )
            MaterialSortOption.LOCATION_ASC -> filtered.sortedWith(
                compareBy<Material> { it.location.lowercase() }.thenBy { it.partNumber }
            )
        }
    }

    private fun <T> Flow<UiState<T>>.catchFirestoreError(): Flow<UiState<T>> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing materials", root)
            emit(UiState.Error(root.message ?: "Failed to load materials"))
        }

    private data class FilterControls(
        val query: String,
        val selectedLine: String?,
        val selectedStatusFilter: MaterialStatus?,
        val sortOption: MaterialSortOption,
        val factoryLines: List<String>
    )

    private data class SelectionControls(
        val isSelectionMode: Boolean,
        val selectedPartNumbers: Set<String>,
        val recentScans: List<String>,
        val isSubmitting: Boolean,
        val actionFeedback: ActionFeedback?
    )

    private companion object {
        const val TAG = "MaterialsViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
