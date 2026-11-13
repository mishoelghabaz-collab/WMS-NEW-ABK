package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DiscrepancyRecord
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
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

data class AdminUiState(
    val activeFactoryId: String = "",
    val isAdmin: Boolean = false,
    val usersState: UiState<List<UserProfile>> = UiState.Success(emptyList()),
    val discrepanciesState: UiState<List<DiscrepancyRecord>> = UiState.Loading,
    val isSubmitting: Boolean = false,
    val feedback: ActionFeedback? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(
    private val repository: WmsRepository
) : ViewModel() {

    private val _activeFactoryId = MutableStateFlow("")
    private val _isAdmin = MutableStateFlow(false)
    private val _isSubmitting = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<ActionFeedback?>(null)

    private val usersFlow: StateFlow<UiState<List<UserProfile>>> =
        _isAdmin
            .flatMapLatest { admin ->
                if (!admin) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    repository.observeAllUsers()
                        .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
                        .catchFirestoreError("users")
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Success(emptyList())
            )

    private val discrepanciesFlow: StateFlow<UiState<List<DiscrepancyRecord>>> =
        _activeFactoryId
            .flatMapLatest { factoryId ->
                if (factoryId.isBlank()) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    repository.observeDiscrepancies(factoryId)
                        .map<List<DiscrepancyRecord>, UiState<List<DiscrepancyRecord>>> {
                            UiState.Success(it)
                        }
                        .catchFirestoreError("discrepancies")
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val uiState: StateFlow<AdminUiState> = combine(
        _activeFactoryId,
        _isAdmin,
        usersFlow,
        discrepanciesFlow,
        _isSubmitting,
        _feedback
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        AdminUiState(
            activeFactoryId = values[0] as String,
            isAdmin = values[1] as Boolean,
            usersState = values[2] as UiState<List<UserProfile>>,
            discrepanciesState = values[3] as UiState<List<DiscrepancyRecord>>,
            isSubmitting = values[4] as Boolean,
            feedback = values[5] as ActionFeedback?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AdminUiState()
    )

    fun syncContext(factoryId: String, isAdmin: Boolean) {
        _activeFactoryId.value = factoryId
        _isAdmin.value = isAdmin
    }

    fun updateUserAccess(
        targetUid: String,
        role: UserRole,
        factoryIdsCsv: String
    ) {
        val factoryIds = factoryIdsCsv
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()

        viewModelScope.launch {
            _isSubmitting.value = true
            repository.updateUserRoleAndFactories(
                targetUid = targetUid,
                role = role,
                factoryIds = factoryIds
            ).onSuccess {
                _feedback.value = ActionFeedback("User access updated in Firestore.", isError = false)
            }.onFailure { error ->
                _feedback.value = ActionFeedback(
                    message = error.localizedMessage ?: "Failed to update user access",
                    isError = true
                )
            }
            _isSubmitting.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    private fun <T> Flow<UiState<T>>.catchFirestoreError(streamName: String): Flow<UiState<T>> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing $streamName", root)
            emit(UiState.Error(root.message ?: "Failed to load $streamName"))
        }

    private companion object {
        const val TAG = "AdminViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
