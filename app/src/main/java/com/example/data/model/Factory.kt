package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Factory(
    @DocumentId val id: String = "",
    val code: String = "",
    val name: String = "",
    val productionLines: List<String> = emptyList(),
    val zones: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWriteMap(): Map<String, Any> = mapOf(
        "code" to code,
        "name" to name,
        "productionLines" to productionLines,
        "zones" to zones,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
