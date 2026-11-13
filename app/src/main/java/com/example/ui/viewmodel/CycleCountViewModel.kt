package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CycleCountRecord
import com.example.data.model.Material
import com.example.data.model.UiState
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

data class CycleCountUiState(
    val activeFactoryId: String = "",
    val materials: List<Material> = emptyList(),
    val recentCountsState: UiState<List<CycleCountRecord>> = UiState.Loading,
    val activeMaterial: Material? = null,
    val actualQtyInput: String = "",
    val adjustmentNotes: String = "",
    val notFoundPartNumber: String? = null,
    val isSubmitting: Boolean = false,
    val feedback: ActionFeedback? = null
) {
    val parsedActualQty: Long?
        get() = actualQtyInput.toLongOrNull()

    val variance: Long?
        get() {
            val mat = activeMaterial ?: return null
            val actual = parsedActualQty ?: return null
            return actual - mat.availableQty
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CycleCountViewModel(
    private val repository: WmsRepository,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val currentUserName: String
) : ViewModel() {

    private val _activeFactoryId = MutableStateFlow("")
    private val _selectedPartNumber = MutableStateFlow<String?>(null)
    private val _actualQtyInput = MutableStateFlow("")
    private val _adjustmentNotes = MutableStateFlow("")
    private val _notFoundPartNumber = MutableStateFlow<String?>(null)
    private val _isSubmitting = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<ActionFeedback?>(null)

    private val materialsFlow: StateFlow<List<Material>> =
        _activeFactoryId
            .flatMapLatest { factoryId ->
                if (factoryId.isBlank()) {
                    flowOf(emptyList())
                } else {
                    repository.observeMaterials(factoryId)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            Log.w(TAG, "Error observing materials in cycle count", error)
                            emit(emptyList())
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    private val recentCountsFlow: StateFlow<UiState<List<CycleCountRecord>>> =
        _activeFactoryId
            .flatMapLatest { factoryId ->
                if (factoryId.isBlank()) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    repository.observeCycleCounts(factoryId)
                        .map<List<CycleCountRecord>, UiState<List<CycleCountRecord>>> {
                            UiState.Success(it)
                        }
                        .catchFirestoreError()
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val countInputFlow = combine(
        _selectedPartNumber,
        _actualQtyInput,
        _adjustmentNotes,
        _notFoundPartNumber,
        _isSubmitting,
        _feedback
    ) { values ->
        CountInputState(
            selectedPartNumber = values[0] as String?,
            actualQtyInput = values[1] as String,
            adjustmentNotes = values[2] as String,
            notFoundPartNumber = values[3] as String?,
            isSubmitting = values[4] as Boolean,
            feedback = values[5] as ActionFeedback?
        )
    }

    val uiState: StateFlow<CycleCountUiState> = combine(
        _activeFactoryId,
        materialsFlow,
        recentCountsFlow,
        countInputFlow
    ) { factoryId, materials, recentState, input ->
        val activeMat = input.selectedPartNumber?.let { pn ->
            materials.firstOrNull { it.partNumber == pn }
        }
        CycleCountUiState(
            activeFactoryId = factoryId,
            materials = materials,
            recentCountsState = recentState,
            activeMaterial = activeMat,
            actualQtyInput = input.actualQtyInput,
            adjustmentNotes = input.adjustmentNotes,
            notFoundPartNumber = input.notFoundPartNumber,
            isSubmitting = input.isSubmitting,
            feedback = input.feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CycleCountUiState()
    )

    fun syncFactory(factoryId: String) {
        if (_activeFactoryId.value != factoryId) {
            _activeFactoryId.value = factoryId
            clearActiveCount()
        }
    }

    fun selectOrLookupPart(partNumber: String) {
        val clean = partNumber.trim()
        val factoryId = _activeFactoryId.value
        if (!Material.isValidPartNumber(clean) || factoryId.isBlank()) return

        val cached = materialsFlow.value.firstOrNull { it.partNumber == clean }
        if (cached != null) {
            _selectedPartNumber.value = cached.partNumber
            _actualQtyInput.value = ""
            _adjustmentNotes.value = ""
            _notFoundPartNumber.value = null
            return
        }

        viewModelScope.launch {
            repository.getMaterialByPartNumber(factoryId, clean)
                .onSuccess { fetched ->
                    if (fetched != null) {
                        _selectedPartNumber.value = fetched.partNumber
                        _actualQtyInput.value = ""
                        _adjustmentNotes.value = ""
                        _notFoundPartNumber.value = null
                    } else {
                        _selectedPartNumber.value = null
                        _notFoundPartNumber.value = clean
                    }
                }
                .onFailure { error ->
                    _feedback.value = ActionFeedback(
                        message = error.localizedMessage ?: "Lookup failed",
                        isError = true
                    )
                }
        }
    }

    fun appendDigit(digit: Char) {
        if (!digit.isDigit()) return
        val current = _actualQtyInput.value
        if (current.length >= 8) return
        _actualQtyInput.value = if (current == "0") digit.toString() else current + digit
    }

    fun backspaceDigit() {
        val current = _actualQtyInput.value
        if (current.isNotEmpty()) {
            _actualQtyInput.value = current.dropLast(1)
        }
    }

    fun clearDigits() {
        _actualQtyInput.value = ""
    }

    fun updateAdjustmentNotes(notes: String) {
        _adjustmentNotes.value = notes.take(200)
    }

    fun clearActiveCount() {
        _selectedPartNumber.value = null
        _actualQtyInput.value = ""
        _adjustmentNotes.value = ""
        _notFoundPartNumber.value = null
    }

    fun submitCount() {
        val factoryId = _activeFactoryId.value
        val activeMaterial = uiState.value.activeMaterial ?: return
        val actualQty = _actualQtyInput.value.toLongOrNull()
        if (actualQty == null || actualQty < 0L) {
            _feedback.value = ActionFeedback("Enter a valid physical quantity (>= 0).", isError = true)
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val operatorDisplay = currentUserName.ifBlank { currentUserEmail }.ifBlank { currentUserId }
            repository.submitCycleCount(
                factoryId = factoryId,
                partNumber = activeMaterial.partNumber,
                actualQty = actualQty,
                operatorName = operatorDisplay,
                notes = _adjustmentNotes.value
            ).onSuccess { record ->
                val summary = if (record.variance == 0L) {
                    "Verified ${record.partNumber}: 0 variance."
                } else {
                    "Cycle Count Adjustment ${record.partNumber}: ${record.systemQty} -> ${record.actualQty} (Var ${if (record.variance > 0) "+" else ""}${record.variance})."
                }
                _feedback.value = ActionFeedback(summary, isError = false)
                clearActiveCount()
            }.onFailure { error ->
                _feedback.value = ActionFeedback(
                    message = error.localizedMessage ?: "Failed to save cycle count",
                    isError = true
                )
            }
            _isSubmitting.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    private fun <T> Flow<UiState<T>>.catchFirestoreError(): Flow<UiState<T>> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing cycleCounts", root)
            emit(UiState.Error(root.message ?: "Failed to load cycle counts"))
        }

    private data class CountInputState(
        val selectedPartNumber: String?,
        val actualQtyInput: String,
        val adjustmentNotes: String,
        val notFoundPartNumber: String?,
        val isSubmitting: Boolean,
        val feedback: ActionFeedback?
    )

    private companion object {
        const val TAG = "CycleCountViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
