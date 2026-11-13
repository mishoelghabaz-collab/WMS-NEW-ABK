package com.example.data.model

enum class UserRole {
    ADMIN,
    SUPERVISOR,
    OPERATOR,
    VIEWER;

    fun canTransact(): Boolean = this == ADMIN || this == SUPERVISOR || this == OPERATOR
    fun canManageMaterials(): Boolean = this == ADMIN || this == SUPERVISOR
    fun isAdmin(): Boolean = this == ADMIN

    companion object {
        fun fromString(value: String?): UserRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: VIEWER
    }
}

enum class MaterialStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK;

    companion object {
        fun fromQuantity(availableQty: Long, minThreshold: Long): MaterialStatus = when {
            availableQty <= 0L -> OUT_OF_STOCK
            availableQty <= minThreshold -> LOW_STOCK
            else -> IN_STOCK
        }

        fun fromString(value: String?): MaterialStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OUT_OF_STOCK
    }
}

enum class TransactionType {
    RECEIVE,
    ISSUE,
    TRANSFER;

    companion object {
        fun fromString(value: String?): TransactionType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RECEIVE
    }
}

enum class AppLanguage(val code: String, val isRtl: Boolean) {
    EN("en", false),
    AR("ar", true);

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EN
    }
}
