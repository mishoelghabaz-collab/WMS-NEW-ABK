package com.example.data.model

enum class ThemeMode(val code: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        fun fromCode(code: String?): ThemeMode = when (code?.lowercase()) {
            "light" -> LIGHT
            "system" -> SYSTEM
            else -> DARK
        }
    }
}
