package com.example.data.voice

import com.example.data.model.Material

enum class VoiceCommandAction {
    ISSUE,
    RECEIVE,
    CYCLE_COUNT
}

data class ParsedVoiceCommand(
    val action: VoiceCommandAction,
    val quantity: Long?,
    val partNumber: String,
    val rawTranscript: String
)

sealed interface VoiceParseResult {
    data class Success(val command: ParsedVoiceCommand) : VoiceParseResult
    data class Failure(val reason: VoiceParseError) : VoiceParseResult
}

enum class VoiceParseError {
    EMPTY_INPUT,
    MISSING_ACTION,
    MISSING_PART_NUMBER,
    MISSING_QUANTITY
}

object VoiceCommandParser {

    private val PART_8_DIGIT_REGEX = Regex("\\b(\\d{8})\\b")
    private val PART_SPACED_8_DIGIT_REGEX = Regex("\\b(\\d{4})\\s+(\\d{4})\\b")
    private val QUANTITY_REGEX = Regex("\\b(\\d{1,6})\\b")

    private val ISSUE_KEYWORDS = listOf(
        "issue", "issued", "remove", "deduct", "withdraw", "out",
        "اصرف", "صرف", "اسحب", "سحب", "اخراج", "إخراج", "خصم", "صرفية"
    )

    private val RECEIVE_KEYWORDS = listOf(
        "return", "returned", "receive", "received", "add", "restock", "plus",
        "ريتيرن", "ريترن", "رجع", "ارجاع", "إرجاع", "مرتجع",
        "زيادة", "زود", "اضافة", "إضافة", "اضف", "أضف", "استلام", "توريد"
    )

    private val CYCLE_COUNT_KEYWORDS = listOf(
        "cycle count", "count", "counted", "counting", "audit", "verify", "check",
        "جرد", "اجرد", "عد", "عدّ", "احسب", "فحص", "مطابقة"
    )

    fun parse(rawInput: String): VoiceParseResult {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return VoiceParseResult.Failure(VoiceParseError.EMPTY_INPUT)
        }

        val normalized = normalizeArabicDigits(trimmed)
        val lower = normalized.lowercase()

        // 1. Detect Action
        val action = detectAction(lower)
            ?: return VoiceParseResult.Failure(VoiceParseError.MISSING_ACTION)

        // 2. Extract EXACTLY 8-digit Part Number
        val strictPart = PART_8_DIGIT_REGEX.find(normalized)
        val spacedPart = if (strictPart == null) PART_SPACED_8_DIGIT_REGEX.find(normalized) else null

        val partNumber: String
        val withoutPart: String
        when {
            strictPart != null -> {
                partNumber = strictPart.groupValues[1]
                withoutPart = normalized.replaceFirst(strictPart.value, " ")
            }
            spacedPart != null -> {
                partNumber = spacedPart.groupValues[1] + spacedPart.groupValues[2]
                withoutPart = normalized.replaceFirst(spacedPart.value, " ")
            }
            else -> return VoiceParseResult.Failure(VoiceParseError.MISSING_PART_NUMBER)
        }

        if (!Material.isValidPartNumber(partNumber)) {
            return VoiceParseResult.Failure(VoiceParseError.MISSING_PART_NUMBER)
        }

        // 3. Extract Quantity from remaining numbers
        val qtyMatches = QUANTITY_REGEX.findAll(withoutPart)
            .mapNotNull { it.groupValues[1].toLongOrNull() }
            .toList()

        val quantity = qtyMatches.firstOrNull()

        // ISSUE and RECEIVE strictly require positive quantity (> 0).
        // CYCLE_COUNT allows quantity >= 0 or null (if user just wants to open Cycle Count for that SKU).
        when (action) {
            VoiceCommandAction.ISSUE,
            VoiceCommandAction.RECEIVE -> {
                if (quantity == null || quantity <= 0L) {
                    return VoiceParseResult.Failure(VoiceParseError.MISSING_QUANTITY)
                }
            }
            VoiceCommandAction.CYCLE_COUNT -> {
                if (quantity != null && quantity < 0L) {
                    return VoiceParseResult.Failure(VoiceParseError.MISSING_QUANTITY)
                }
            }
        }

        return VoiceParseResult.Success(
            ParsedVoiceCommand(
                action = action,
                quantity = quantity,
                partNumber = partNumber,
                rawTranscript = trimmed
            )
        )
    }

    private fun detectAction(lowerText: String): VoiceCommandAction? {
        // Normalize common Arabic hamza variants for reliable matching
        val normalizedArabic = lowerText
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')

        val tokens = normalizedArabic.split(Regex("[\\s,،.!?؛:]+")).filter { it.isNotBlank() }

        fun matchesAny(keywords: List<String>): Boolean =
            keywords.any { kw ->
                val normKw = kw
                    .replace('أ', 'ا')
                    .replace('إ', 'ا')
                    .replace('آ', 'ا')
                    .replace('ى', 'ي')
                    .replace('ة', 'ه')
                if (normKw.contains(' ')) {
                    normalizedArabic.contains(normKw)
                } else {
                    tokens.any { token ->
                        token == normKw || token == "ال$normKw" || token == "و$normKw"
                    }
                }
            }

        return when {
            matchesAny(CYCLE_COUNT_KEYWORDS) -> VoiceCommandAction.CYCLE_COUNT
            matchesAny(ISSUE_KEYWORDS) -> VoiceCommandAction.ISSUE
            matchesAny(RECEIVE_KEYWORDS) -> VoiceCommandAction.RECEIVE
            else -> null
        }
    }

    fun normalizeArabicDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
