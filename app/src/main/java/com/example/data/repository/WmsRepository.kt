package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CycleCountRecord
import com.example.data.model.DiscrepancyRecord
import com.example.data.model.Factory
import com.example.data.model.Material
import com.example.data.model.MaterialStatus
import com.example.data.model.StockTransaction
import com.example.data.model.TransactionType
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WmsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.logFirestoreErrors(
        operationType: OperationType,
        path: String,
        uid: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path, auth)
        }
        throw error
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val activeUid = requireUserId()
        val docRef = db.collection(COLLECTION_USERS).document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .logFirestoreErrors(OperationType.GET, docRef.path, activeUid)
        )
    }

    suspend fun ensureUserProfile(
        uid: String,
        displayName: String,
        email: String,
        isBootstrappedAdmin: Boolean = false
    ): Result<UserProfile> = runCatching {
        requireUserId()
        val docRef = db.collection(COLLECTION_USERS).document(uid)
        val snapshot = docRef.get().await()
        if (snapshot.exists()) {
            snapshot.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: error("Failed to parse existing user profile")
        } else {
            val initialRole = if (isBootstrappedAdmin) UserRole.ADMIN.name else UserRole.VIEWER.name
            val profile = UserProfile(
                uid = uid,
                displayName = displayName.ifBlank { email.substringBefore("@") },
                email = email,
                role = initialRole,
                factoryIds = emptyList()
            )
            docRef.set(profile.toCreateMap()).await()
            profile
        }
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(error, OperationType.WRITE, "$COLLECTION_USERS/$uid", auth)
    }

    fun observeFactories(factoryIds: List<String>, isAdmin: Boolean): Flow<List<Factory>> = flow {
        val uid = requireUserId()
        val query: Query = if (isAdmin) {
            db.collection(COLLECTION_FACTORIES)
        } else {
            if (factoryIds.isEmpty()) {
                emit(emptyList())
                return@flow
            }
            db.collection(COLLECTION_FACTORIES)
                .whereIn("code", factoryIds.take(10))
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Factory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.code }
                }
                .logFirestoreErrors(OperationType.LIST, COLLECTION_FACTORIES, uid)
        )
    }

    suspend fun upsertFactory(factory: Factory): Result<Unit> = runCatching {
        requireUserId()
        require(factory.code.isNotBlank()) { "Factory code must not be blank" }
        val docRef = db.collection(COLLECTION_FACTORIES).document(factory.code)
        docRef.set(factory.toWriteMap()).await()
        Unit
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(error, OperationType.WRITE, "$COLLECTION_FACTORIES/${factory.code}", auth)
    }

    fun observeMaterials(factoryId: String): Flow<List<Material>> = flow {
        val uid = requireUserId()
        if (factoryId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val query = db.collection(COLLECTION_MATERIALS)
            .whereEqualTo("factoryId", factoryId)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Material::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.partNumber }
                }
                .logFirestoreErrors(OperationType.LIST, COLLECTION_MATERIALS, uid)
        )
    }

    suspend fun getMaterialByPartNumber(factoryId: String, partNumber: String): Result<Material?> =
        runCatching {
            requireUserId()
            require(Material.isValidPartNumber(partNumber)) {
                "Part number must be exactly 8 digits."
            }
            val docId = Material.documentId(factoryId, partNumber)
            val snapshot = db.collection(COLLECTION_MATERIALS).document(docId).get().await()
            if (!snapshot.exists()) null
            else snapshot.toObject(
                Material::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            handleFirestoreError(
                error,
                OperationType.GET,
                "$COLLECTION_MATERIALS/${factoryId}_${partNumber}",
                auth
            )
        }

    suspend fun createMaterial(material: Material): Result<Material> = runCatching {
        val uid = requireUserId()
        require(Material.isValidPartNumber(material.partNumber)) {
            "Part number must be exactly 8 digits."
        }
        require(material.availableQty >= 0L) { "Available quantity cannot be negative." }
        val docId = Material.documentId(material.factoryId, material.partNumber)
        val docRef = db.collection(COLLECTION_MATERIALS).document(docId)
        val computedStatus = MaterialStatus.fromQuantity(material.availableQty, material.minThreshold).name
        val normalized = material.copy(
            id = docId,
            status = computedStatus,
            updatedBy = uid
        )
        docRef.set(normalized.toCreateMap(uid)).await()
        normalized
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(
            error,
            OperationType.CREATE,
            "$COLLECTION_MATERIALS/${material.factoryId}_${material.partNumber}",
            auth
        )
    }

    /**
     * Executes an atomic RECEIVE, ISSUE, or TRANSFER using Firestore runTransaction().
     * Guarantees availableQty never drops below 0 and commits both the Material update
     * and the immutable StockTransaction log atomically.
     */
    suspend fun executeAtomicStockTransaction(
        factoryId: String,
        partNumber: String,
        type: TransactionType,
        qty: Long,
        operatorName: String,
        targetLocation: String = "",
        notes: String = ""
    ): Result<StockTransaction> = runCatching {
        val uid = requireUserId()
        require(factoryId.isNotBlank()) { "Factory ID must not be blank." }
        require(Material.isValidPartNumber(partNumber)) {
            "Part number must be exactly 8 digits."
        }
        require(qty > 0L) { "Transaction quantity must be greater than zero." }
        if (type == TransactionType.TRANSFER) {
            require(targetLocation.isNotBlank()) {
                "Target location is required for TRANSFER transactions."
            }
        }

        val materialId = Material.documentId(factoryId, partNumber)
        val materialRef = db.collection(COLLECTION_MATERIALS).document(materialId)
        val txRef = db.collection(COLLECTION_TRANSACTIONS).document()

        db.runTransaction { transaction ->
            val snapshot = transaction.get(materialRef)
            if (!snapshot.exists()) {
                throw IllegalStateException("Material $materialId not found in plant $factoryId.")
            }

            val currentQty = snapshot.getLong("availableQty") ?: 0L
            val minThreshold = snapshot.getLong("minThreshold") ?: Material.DEFAULT_MIN_THRESHOLD
            val currentLocation = snapshot.getString("location").orEmpty()
            val materialName = snapshot.getString("name").orEmpty()

            val newQty = when (type) {
                TransactionType.RECEIVE -> currentQty + qty
                TransactionType.ISSUE -> {
                    val remaining = currentQty - qty
                    if (remaining < 0L) {
                        throw IllegalStateException(
                            "Insufficient stock for $partNumber: available $currentQty, requested $qty."
                        )
                    }
                    remaining
                }
                TransactionType.TRANSFER -> currentQty
            }

            val updatedLocation = if (type == TransactionType.TRANSFER) {
                targetLocation.trim()
            } else {
                currentLocation
            }
            val newStatus = MaterialStatus.fromQuantity(newQty, minThreshold).name

            val materialUpdates = mapOf(
                "availableQty" to newQty,
                "location" to updatedLocation,
                "status" to newStatus,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            transaction.update(materialRef, materialUpdates)

            val stockTransaction = StockTransaction(
                id = txRef.id,
                factoryId = factoryId,
                materialId = materialId,
                partNumber = partNumber,
                materialName = materialName,
                type = type.name,
                qty = qty,
                previousQty = currentQty,
                newQty = newQty,
                user = operatorName.ifBlank { auth.currentUser?.email ?: uid },
                userId = uid,
                location = currentLocation,
                targetLocation = if (type == TransactionType.TRANSFER) updatedLocation else "",
                notes = notes.trim()
            )
            transaction.set(txRef, stockTransaction.toCreateMap())
            stockTransaction
        }.await()
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(
            error,
            OperationType.WRITE,
            "$COLLECTION_MATERIALS/${factoryId}_${partNumber}",
            auth
        )
    }

    fun observeTransactions(factoryId: String): Flow<List<StockTransaction>> = flow {
        val uid = requireUserId()
        if (factoryId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val query = db.collection(COLLECTION_TRANSACTIONS)
            .whereEqualTo("factoryId", factoryId)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StockTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.timestamp?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, COLLECTION_TRANSACTIONS, uid)
        )
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COLLECTION_USERS)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.displayName.lowercase() }
                }
                .logFirestoreErrors(OperationType.LIST, COLLECTION_USERS, uid)
        )
    }

    suspend fun updateUserRoleAndFactories(
        targetUid: String,
        role: UserRole,
        factoryIds: List<String>
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection(COLLECTION_USERS).document(targetUid)
        docRef.update(
            mapOf(
                "role" to role.name,
                "factoryIds" to factoryIds.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(error, OperationType.UPDATE, "$COLLECTION_USERS/$targetUid", auth)
    }

    fun observeCycleCounts(factoryId: String): Flow<List<CycleCountRecord>> = flow {
        val uid = requireUserId()
        if (factoryId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val query = db.collection(COLLECTION_CYCLE_COUNTS)
            .whereEqualTo("factoryId", factoryId)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CycleCountRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.timestamp?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, COLLECTION_CYCLE_COUNTS, uid)
        )
    }

    fun observeDiscrepancies(factoryId: String): Flow<List<DiscrepancyRecord>> = flow {
        val uid = requireUserId()
        if (factoryId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val query = db.collection(COLLECTION_DISCREPANCIES)
            .whereEqualTo("factoryId", factoryId)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DiscrepancyRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.timestamp?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, COLLECTION_DISCREPANCIES, uid)
        )
    }

    /**
     * Submits a physical cycle count using Firestore runTransaction().
     * Never allows negative stock. If variance != 0, atomically updates the Material's
     * availableQty/status and records both CycleCountRecord and DiscrepancyRecord.
     */
    suspend fun submitCycleCount(
        factoryId: String,
        partNumber: String,
        actualQty: Long,
        operatorName: String,
        notes: String = ""
    ): Result<CycleCountRecord> = runCatching {
        val uid = requireUserId()
        require(factoryId.isNotBlank()) { "Factory ID must not be blank." }
        require(Material.isValidPartNumber(partNumber)) {
            "Part number must be exactly 8 digits."
        }
        require(actualQty >= 0L) { "Physical count cannot be negative." }

        val materialId = Material.documentId(factoryId, partNumber)
        val materialRef = db.collection(COLLECTION_MATERIALS).document(materialId)
        val cycleCountRef = db.collection(COLLECTION_CYCLE_COUNTS).document()
        val discrepancyRef = db.collection(COLLECTION_DISCREPANCIES).document()
        val countedByDisplay = operatorName.ifBlank { auth.currentUser?.email ?: uid }

        db.runTransaction { transaction ->
            val snapshot = transaction.get(materialRef)
            if (!snapshot.exists()) {
                throw IllegalStateException("Material $materialId not found in plant $factoryId.")
            }

            val systemQty = snapshot.getLong("availableQty") ?: 0L
            val minThreshold = snapshot.getLong("minThreshold") ?: Material.DEFAULT_MIN_THRESHOLD
            val location = snapshot.getString("location").orEmpty()
            val materialName = snapshot.getString("name").orEmpty()
            val variance = actualQty - systemQty

            if (variance != 0L) {
                val newStatus = MaterialStatus.fromQuantity(actualQty, minThreshold).name
                transaction.update(
                    materialRef,
                    mapOf(
                        "availableQty" to actualQty,
                        "status" to newStatus,
                        "updatedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val cycleStatus = if (variance == 0L) {
                CycleCountRecord.STATUS_MATCHED
            } else {
                CycleCountRecord.STATUS_ADJUSTED
            }

            val cycleCount = CycleCountRecord(
                id = cycleCountRef.id,
                factoryId = factoryId,
                materialId = materialId,
                partNumber = partNumber,
                materialName = materialName,
                location = location,
                systemQty = systemQty,
                actualQty = actualQty,
                variance = variance,
                status = cycleStatus,
                countedBy = countedByDisplay,
                countedByUid = uid,
                notes = notes.trim()
            )
            transaction.set(cycleCountRef, cycleCount.toCreateMap())

            if (variance != 0L) {
                val discrepancy = DiscrepancyRecord(
                    id = discrepancyRef.id,
                    factoryId = factoryId,
                    cycleCountId = cycleCountRef.id,
                    materialId = materialId,
                    partNumber = partNumber,
                    materialName = materialName,
                    location = location,
                    systemQty = systemQty,
                    actualQty = actualQty,
                    variance = variance,
                    resolutionStatus = DiscrepancyRecord.STATUS_ADJUSTED,
                    reportedBy = countedByDisplay,
                    reportedByUid = uid,
                    notes = notes.trim()
                )
                transaction.set(discrepancyRef, discrepancy.toCreateMap())
            }

            cycleCount
        }.await()
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(
            error,
            OperationType.WRITE,
            "$COLLECTION_CYCLE_COUNTS/${factoryId}_$partNumber",
            auth
        )
    }

    enum class OcrUpsertOutcome {
        CREATED,
        UPDATED
    }

    /**
     * Atomically updates an existing material's location, productionLine, and requiredQty,
     * or creates a new material document if the partNumber does not yet exist in the plant.
     */
    suspend fun upsertMaterialFromOcr(
        factoryId: String,
        partNumber: String,
        location: String,
        productionLine: String,
        requiredQty: Long,
        materialName: String = ""
    ): Result<OcrUpsertOutcome> = runCatching {
        val uid = requireUserId()
        require(factoryId.isNotBlank()) { "Factory ID must not be blank." }
        require(Material.isValidPartNumber(partNumber)) {
            "Part number must be exactly 8 digits."
        }
        require(location.isNotBlank()) { "Location must not be blank." }
        require(productionLine.isNotBlank()) { "Production line must not be blank." }
        require(requiredQty >= 0L) { "Required quantity cannot be negative." }

        val docId = Material.documentId(factoryId, partNumber)
        val materialRef = db.collection(COLLECTION_MATERIALS).document(docId)
        val cleanLocation = location.trim().uppercase()
        val cleanLine = productionLine.trim()

        db.runTransaction { transaction ->
            val snapshot = transaction.get(materialRef)
            if (snapshot.exists()) {
                transaction.update(
                    materialRef,
                    mapOf(
                        "location" to cleanLocation,
                        "productionLine" to cleanLine,
                        "requiredQty" to requiredQty,
                        "updatedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                OcrUpsertOutcome.UPDATED
            } else {
                val newMaterial = Material(
                    id = docId,
                    factoryId = factoryId,
                    partNumber = partNumber,
                    name = materialName.trim().ifBlank { "SKU $partNumber" },
                    availableQty = 0L,
                    minThreshold = Material.DEFAULT_MIN_THRESHOLD,
                    requiredQty = requiredQty,
                    location = cleanLocation,
                    productionLine = cleanLine,
                    status = MaterialStatus.OUT_OF_STOCK.name,
                    unit = Material.DEFAULT_UNIT,
                    updatedBy = uid
                )
                transaction.set(materialRef, newMaterial.toCreateMap(uid))
                OcrUpsertOutcome.CREATED
            }
        }.await()
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(
            error,
            OperationType.WRITE,
            "$COLLECTION_MATERIALS/${factoryId}_$partNumber",
            auth
        )
    }

    companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_FACTORIES = "factories"
        const val COLLECTION_MATERIALS = "materials"
        const val COLLECTION_TRANSACTIONS = "transactions"
        const val COLLECTION_CYCLE_COUNTS = "cycleCounts"
        const val COLLECTION_DISCREPANCIES = "discrepancies"
    }
}
