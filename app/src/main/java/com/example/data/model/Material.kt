package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Material(
    @DocumentId val id: String = "",
    val factoryId: String = "",
    val partNumber: String = "",
    val name: String = "",
    val availableQty: Long = 0L,
    val minThreshold: Long = DEFAULT_MIN_THRESHOLD,
    val requiredQty: Long = 0L,
    val location: String = "",
    val productionLine: String = "",
    val status: String = MaterialStatus.OUT_OF_STOCK.name,
    val unit: String = DEFAULT_UNIT,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): MaterialStatus = MaterialStatus.fromString(status)

    fun toCreateMap(updatedByUid: String): Map<String, Any> {
        val computedStatus = MaterialStatus.fromQuantity(availableQty, minThreshold).name
        return mapOf(
            "factoryId" to factoryId,
            "partNumber" to partNumber,
            "name" to name,
            "availableQty" to availableQty,
            "minThreshold" to minThreshold,
            "requiredQty" to requiredQty,
            "location" to location,
            "productionLine" to productionLine,
            "status" to computedStatus,
            "unit" to unit,
            "updatedBy" to updatedByUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    companion object {
        const val DEFAULT_MIN_THRESHOLD = 10L
        const val DEFAULT_UNIT = "PCS"
        val PART_NUMBER_REGEX = Regex("^\\d{8}$")

        fun isValidPartNumber(partNumber: String): Boolean = PART_NUMBER_REGEX.matches(partNumber)

        fun documentId(factoryId: String, partNumber: String): String {
            require(factoryId.isNotBlank()) { "Factory ID must not be blank" }
            require(isValidPartNumber(partNumber)) { "Part number must be exactly 8 digits" }
            return "${factoryId}_${partNumber}"
        }
    }
}
