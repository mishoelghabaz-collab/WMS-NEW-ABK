package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CycleCountRecord(
    @DocumentId val id: String = "",
    val factoryId: String = "",
    val materialId: String = "",
    val partNumber: String = "",
    val materialName: String = "",
    val location: String = "",
    val systemQty: Long = 0L,
    val actualQty: Long = 0L,
    val variance: Long = 0L,
    val status: String = STATUS_MATCHED,
    val countedBy: String = "",
    val countedByUid: String = "",
    val notes: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "factoryId" to factoryId,
            "materialId" to materialId,
            "partNumber" to partNumber,
            "materialName" to materialName,
            "location" to location,
            "systemQty" to systemQty,
            "actualQty" to actualQty,
            "variance" to variance,
            "status" to status,
            "countedBy" to countedBy,
            "countedByUid" to countedByUid,
            "timestamp" to FieldValue.serverTimestamp()
        )
        if (notes.isNotBlank()) {
            base["notes"] = notes
        }
        return base
    }

    companion object {
        const val STATUS_MATCHED = "MATCHED"
        const val STATUS_ADJUSTED = "ADJUSTED"
    }
}
