package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppLanguage
import com.example.data.model.Factory
import com.example.data.model.Material
import com.example.data.model.MaterialStatus
import com.example.data.model.StockTransaction
import com.example.data.model.ThemeMode
import com.example.data.model.TransactionType
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
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

data class DashboardMetrics(
    val totalStock: Long = 0L,
    val uniqueSkus: Int = 0,
    val criticalStockCount: Int = 0,
    val warningCount: Int = 0
)

data class ActionFeedback(
    val message: String,
    val isError: Boolean
)

data class WmsAppState(
    val userProfile: UserProfile? = null,
    val isProfileLoading: Boolean = true,
    val factories: List<Factory> = emptyList(),
    val selectedFactoryId: String = "",
    val materialsState: UiState<List<Material>> = UiState.Loading,
    val transactionsState: UiState<List<StockTransaction>> = UiState.Loading,
    val metrics: DashboardMetrics = DashboardMetrics(),
    val language: AppLanguage = AppLanguage.EN,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val isSubmittingAction: Boolean = false,
    val feedback: ActionFeedback? = null
) {
    val activeRole: UserRole
        get() = userProfile?.roleEnum() ?: UserRole.VIEWER

    val activeFactory: Factory?
        get() = factories.firstOrNull { it.code == selectedFactoryId } ?: factories.firstOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
class WmsViewModel(
    private val repository: WmsRepository,
    private val preferences: WmsPreferencesRepository,
    private val currentUserId: String,
    private val currentUserName: String,
    private val currentUserEmail: String,
    private val isEmailVerified: Boolean
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<ActionFeedback?>(null)

    private val userProfileFlow: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catchFirestoreError("userProfile")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val factoriesFlow: StateFlow<List<Factory>> =
        userProfileFlow
            .flatMapLatest { profileState ->
                val profile = (profileState as? UiState.Success)?.data
                if (profile == null) {
                    flowOf(emptyList())
                } else {
                    val isAdmin = profile.roleEnum().isAdmin()
                    repository.observeFactories(profile.factoryIds, isAdmin)
                        .catch { error ->
                            val firestoreError = unwrapFirestoreError(error)
                            if (firestoreError == null && error is CancellationException) throw error
                            Log.w(TAG, "Error observing factories", firestoreError ?: error)
                            emit(emptyList())
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    private val selectedFactoryIdFlow: StateFlow<String> =
        combine(
            preferences.selectedFactoryId,
            factoriesFlow
        ) { persistedId, factories ->
            when {
                persistedId != null && factories.any { it.code == persistedId } -> persistedId
                factories.isNotEmpty() -> factories.first().code
                else -> ""
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ""
        )

    private val materialsFlow: StateFlow<UiState<List<Material>>> =
        selectedFactoryIdFlow
            .flatMapLatest { factoryId ->
                if (factoryId.isBlank()) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    repository.observeMaterials(factoryId)
                        .map<List<Material>, UiState<List<Material>>> { UiState.Success(it) }
                        .catchFirestoreError("materials")
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val transactionsFlow: StateFlow<UiState<List<StockTransaction>>> =
        selectedFactoryIdFlow
            .flatMapLatest { factoryId ->
                if (factoryId.isBlank()) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    repository.observeTransactions(factoryId)
                        .map<List<StockTransaction>, UiState<List<StockTransaction>>> {
                            UiState.Success(it)
                        }
                        .catchFirestoreError("transactions")
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val languageFlow: StateFlow<AppLanguage> =
        preferences.appLanguage.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppLanguage.EN
        )

    private val themeModeFlow: StateFlow<ThemeMode> =
        preferences.themeMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ThemeMode.DARK
        )

    private val coreDataFlow = combine(
        userProfileFlow,
        factoriesFlow,
        selectedFactoryIdFlow,
        materialsFlow,
        transactionsFlow
    ) { profileState, factories, selectedFactoryId, materialsState, transactionsState ->
        CoreSnapshot(
            profileState = profileState,
            factories = factories,
            selectedFactoryId = selectedFactoryId,
            materialsState = materialsState,
            transactionsState = transactionsState
        )
    }

    val uiState: StateFlow<WmsAppState> = combine(
        coreDataFlow,
        languageFlow,
        themeModeFlow,
        _isSubmitting,
        _feedback
    ) { core, language, themeMode, isSubmitting, feedback ->
        val materials = (core.materialsState as? UiState.Success)?.data.orEmpty()
        val metrics = computeMetrics(materials)
        val profile = (core.profileState as? UiState.Success)?.data
        WmsAppState(
            userProfile = profile,
            isProfileLoading = core.profileState is UiState.Loading,
            factories = core.factories,
            selectedFactoryId = core.selectedFactoryId,
            materialsState = core.materialsState,
            transactionsState = core.transactionsState,
            metrics = metrics,
            language = language,
            themeMode = themeMode,
            isSubmittingAction = isSubmitting,
            feedback = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = WmsAppState()
    )

    init {
        bootstrapUserProfile()
    }

    private fun bootstrapUserProfile() {
        viewModelScope.launch {
            val isBootstrappedAdmin =
                isEmailVerified && currentUserEmail.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
            repository.ensureUserProfile(
                uid = currentUserId,
                displayName = currentUserName,
                email = currentUserEmail,
                isBootstrappedAdmin = isBootstrappedAdmin
            ).onFailure { error ->
                _feedback.value = ActionFeedback(
                    message = error.localizedMessage ?: "Failed to initialize user profile",
                    isError = true
                )
            }
        }
    }

    fun selectFactory(factoryId: String) {
        viewModelScope.launch {
            preferences.setSelectedFactoryId(factoryId)
        }
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (languageFlow.value == AppLanguage.EN) AppLanguage.AR else AppLanguage.EN
            preferences.setAppLanguage(next)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun createFactory(
        code: String,
        name: String,
        productionLinesCsv: String,
        zonesCsv: String
    ) {
        val normalizedCode = code.trim().uppercase()
        val normalizedName = name.trim()
        if (normalizedCode.isBlank() || normalizedName.isBlank()) {
            _feedback.value = ActionFeedback("Plant code and name are required.", isError = true)
            return
        }
        val lines = productionLinesCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("Volvo LFT", "CDPO") }
        val zones = zonesCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("A-01", "B-01") }

        viewModelScope.launch {
            _isSubmitting.value = true
            repository.upsertFactory(
                Factory(
                    id = normalizedCode,
                    code = normalizedCode,
                    name = normalizedName,
                    productionLines = lines,
                    zones = zones
                )
            ).onSuccess {
                preferences.setSelectedFactoryId(normalizedCode)
                _feedback.value = ActionFeedback("Plant $normalizedCode saved to Firestore.", isError = false)
            }.onFailure { error ->
                _feedback.value = ActionFeedback(
                    message = error.localizedMessage ?: "Failed to create plant",
                    isError = true
                )
            }
            _isSubmitting.value = false
        }
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
        val factoryId = selectedFactoryIdFlow.value
        if (factoryId.isBlank()) {
            _feedback.value = ActionFeedback("Select or create a plant first.", isError = true)
            return
        }
        if (!Material.isValidPartNumber(partNumber.trim())) {
            _feedback.value = ActionFeedback("Part number must be exactly 8 digits.", isError = true)
            return
        }
        if (name.isBlank() || location.isBlank() || productionLine.isBlank()) {
            _feedback.value = ActionFeedback("Name, location, and line are required.", isError = true)
            return
        }
        if (initialQty < 0L || minThreshold < 0L) {
            _feedback.value = ActionFeedback("Quantities cannot be negative.", isError = true)
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            repository.createMaterial(
                Material(
                    factoryId = factoryId,
                    partNumber = partNumber.trim(),
                    name = name.trim(),
                    availableQty = initialQty,
                    minThreshold = minThreshold,
                    location = location.trim(),
                    productionLine = productionLine.trim(),
                    unit = unit.trim().ifBlank { Material.DEFAULT_UNIT }
                )
            ).onSuccess {
                _feedback.value = ActionFeedback(
                    "SKU ${partNumber.trim()} registered in $factoryId.",
                    isError = false
                )
            }.onFailure { error ->
                _feedback.value = ActionFeedback(
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
        val factoryId = selectedFactoryIdFlow.value
        if (factoryId.isBlank()) {
            _feedback.value = ActionFeedback("Select an active plant first.", isError = true)
            return
        }
        if (!Material.isValidPartNumber(partNumber.trim())) {
            _feedback.value = ActionFeedback("Part number must be exactly 8 digits.", isError = true)
            return
        }
        if (qty <= 0L) {
            _feedback.value = ActionFeedback("Quantity must be greater than zero.", isError = true)
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val operatorDisplay = uiState.value.userProfile?.displayName
                ?.ifBlank { currentUserEmail }
                ?: currentUserEmail.ifBlank { currentUserId }

            repository.executeAtomicStockTransaction(
                factoryId = factoryId,
                partNumber = partNumber.trim(),
                type = type,
                qty = qty,
                operatorName = operatorDisplay,
                targetLocation = targetLocation.trim(),
                notes = notes.trim()
            ).onSuccess { tx ->
                _feedback.value = ActionFeedback(
                    "${tx.type}: ${tx.partNumber} (${tx.previousQty} -> ${tx.newQty})",
                    isError = false
                )
            }.onFailure { error ->
                _feedback.value = ActionFeedback(
                    message = error.localizedMessage ?: "Transaction rejected",
                    isError = true
                )
            }
            _isSubmitting.value = false
        }
    }

    fun executeVoiceCycleCount(
        partNumber: String,
        actualQty: Long,
        notes: String = "Voice Command"
    ) {
        val factoryId = selectedFactoryIdFlow.value
        if (factoryId.isBlank()) {
            _feedback.value = ActionFeedback("Select an active plant first.", isError = true)
            return
        }
        if (!Material.isValidPartNumber(partNumber.trim())) {
            _feedback.value = ActionFeedback("Part number must be exactly 8 digits.", isError = true)
            return
        }
        if (actualQty < 0L) {
            _feedback.value = ActionFeedback("Physical quantity cannot be negative.", isError = true)
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val operatorDisplay = uiState.value.userProfile?.displayName
                ?.ifBlank { currentUserEmail }
                ?: currentUserEmail.ifBlank { currentUserId }

            repository.submitCycleCount(
                factoryId = factoryId,
                partNumber = partNumber.trim(),
                actualQty = actualQty,
                operatorName = operatorDisplay,
                notes = notes.trim()
            ).onSuccess { record ->
                val summary = if (record.variance == 0L) {
                    "Verified ${record.partNumber}: 0 variance."
                } else {
                    "Cycle Count Adjustment ${record.partNumber}: ${record.systemQty} -> ${record.actualQty} (Var ${if (record.variance > 0) "+" else ""}${record.variance})."
                }
                _feedback.value = ActionFeedback(summary, isError = false)
            }.onFailure { error ->
                _feedback.value = ActionFeedback(
                    message = error.localizedMessage ?: "Failed to save cycle count",
                    isError = true
                )
            }
            _isSubmitting.value = false
        }
    }

    private fun computeMetrics(materials: List<Material>): DashboardMetrics {
        var totalStock = 0L
        var criticalCount = 0
        var warningCount = 0
        for (material in materials) {
            totalStock += material.availableQty
            when (material.statusEnum()) {
                MaterialStatus.OUT_OF_STOCK -> criticalCount++
                MaterialStatus.LOW_STOCK -> warningCount++
                MaterialStatus.IN_STOCK -> Unit
            }
        }
        return DashboardMetrics(
            totalStock = totalStock,
            uniqueSkus = materials.size,
            criticalStockCount = criticalCount,
            warningCount = warningCount
        )
    }

    private fun <T> Flow<UiState<T>>.catchFirestoreError(streamName: String): Flow<UiState<T>> =
        catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing $streamName", root)
            emit(UiState.Error(root.message ?: "Failed to load $streamName"))
        }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private data class CoreSnapshot(
        val profileState: UiState<UserProfile?>,
        val factories: List<Factory>,
        val selectedFactoryId: String,
        val materialsState: UiState<List<Material>>,
        val transactionsState: UiState<List<StockTransaction>>
    )

    private companion object {
        const val TAG = "WmsViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val BOOTSTRAPPED_ADMIN_EMAIL = "mishoelghabaz@gmail.com"
    }
}
