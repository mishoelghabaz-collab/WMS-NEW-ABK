package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class StockTransaction(
    @DocumentId val id: String = "",
    val factoryId: String = "",
    val materialId: String = "",
    val partNumber: String = "",
    val materialName: String = "",
    val type: String = TransactionType.RECEIVE.name,
    val qty: Long = 0L,
    val previousQty: Long = 0L,
    val newQty: Long = 0L,
    val user: String = "",
    val userId: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null,
    val location: String = "",
    val targetLocation: String = "",
    val notes: String = ""
) {
    fun typeEnum(): TransactionType = TransactionType.fromString(type)

    fun toCreateMap(): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "factoryId" to factoryId,
            "materialId" to materialId,
            "partNumber" to partNumber,
            "materialName" to materialName,
            "type" to type,
            "qty" to qty,
            "previousQty" to previousQty,
            "newQty" to newQty,
            "user" to user,
            "userId" to userId,
            "timestamp" to FieldValue.serverTimestamp(),
            "location" to location
        )
        if (targetLocation.isNotBlank()) {
            base["targetLocation"] = targetLocation
        }
        if (notes.isNotBlank()) {
            base["notes"] = notes
        }
        return base
    }
}
