package com.example.data.ocr

import com.example.data.model.Material

data class OcrExtractedRow(
    val rowId: String,
    val partNumber: String,
    val location: String,
    val productionLine: String,
    val requiredQty: String,
    val materialName: String = "",
    val lowConfidencePartNumber: Boolean = false,
    val lowConfidenceLocation: Boolean = false,
    val lowConfidenceLine: Boolean = false,
    val lowConfidenceQty: Boolean = false
) {
    val hasLowConfidence: Boolean
        get() = lowConfidencePartNumber || lowConfidenceLocation || lowConfidenceLine || lowConfidenceQty
}

object OcrMaterialParser {

    private val PART_8_DIGIT_REGEX = Regex("\\b(\\d{8})\\b")
    private val PART_SPACED_8_DIGIT_REGEX = Regex("\\b(\\d{4})\\s+(\\d{4})\\b")
    private val LOCATION_COMPACT_REGEX = Regex("\\b([A-Z]\\d{4})\\b", RegexOption.IGNORE_CASE)
    private val LOCATION_DASHED_REGEX = Regex("\\b([A-Z]-\\d{2}(?:-\\d{2})?)\\b", RegexOption.IGNORE_CASE)
    private val QTY_REGEX = Regex("\\b(\\d{1,5})\\b")

    private val DEFAULT_LINES = listOf("Volvo LFT", "CDPO", "Nissan")

    fun parseTextLines(
        rawLines: List<String>,
        knownLines: List<String> = emptyList()
    ): List<OcrExtractedRow> {
        val allLines = (knownLines + DEFAULT_LINES)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val defaultLine = allLines.firstOrNull() ?: "Volvo LFT"
        val results = mutableListOf<OcrExtractedRow>()
        val seenParts = mutableSetOf<String>()

        var rowCounter = 1
        for (rawLine in rawLines) {
            val cleanedLine = rawLine.trim()
            if (cleanedLine.isBlank()) continue

            val strictPartMatch = PART_8_DIGIT_REGEX.find(cleanedLine)
            val spacedPartMatch = if (strictPartMatch == null) {
                PART_SPACED_8_DIGIT_REGEX.find(cleanedLine)
            } else {
                null
            }

            val partNumber: String
            val lowConfPart: Boolean
            val partMatchedToken: String

            when {
                strictPartMatch != null -> {
                    partNumber = strictPartMatch.groupValues[1]
                    lowConfPart = false
                    partMatchedToken = strictPartMatch.value
                }
                spacedPartMatch != null -> {
                    partNumber = spacedPartMatch.groupValues[1] + spacedPartMatch.groupValues[2]
                    lowConfPart = true
                    partMatchedToken = spacedPartMatch.value
                }
                else -> continue
            }

            if (!seenParts.add(partNumber)) continue

            // Remove matched part number before extracting location and quantity
            var remainder = cleanedLine.replaceFirst(partMatchedToken, " ")

            // Extract Location (e.g., A0101, C0402, F0501 or A-01-01)
            val compactLocMatch = LOCATION_COMPACT_REGEX.find(remainder)
            val dashedLocMatch = if (compactLocMatch == null) {
                LOCATION_DASHED_REGEX.find(remainder)
            } else {
                null
            }
            val location: String
            val lowConfLoc: Boolean
            when {
                compactLocMatch != null -> {
                    location = compactLocMatch.groupValues[1].uppercase()
                    lowConfLoc = false
                    remainder = remainder.replaceFirst(compactLocMatch.value, " ")
                }
                dashedLocMatch != null -> {
                    location = dashedLocMatch.groupValues[1].uppercase()
                    lowConfLoc = false
                    remainder = remainder.replaceFirst(dashedLocMatch.value, " ")
                }
                else -> {
                    location = ""
                    lowConfLoc = true
                }
            }

            // Extract Production Line (fuzzy & case-insensitive)
            val matchedLine = matchProductionLine(remainder, allLines)
            val productionLine: String
            val lowConfLine: Boolean
            if (matchedLine != null) {
                productionLine = matchedLine.first
                lowConfLine = false
                remainder = remainder.replace(matchedLine.second, " ", ignoreCase = true)
            } else {
                productionLine = defaultLine
                lowConfLine = true
            }

            // Extract Required Qty (numeric token near part no / end of row)
            val qtyMatches = QTY_REGEX.findAll(remainder).toList()
            val reqQty: String
            val lowConfQty: Boolean
            when {
                qtyMatches.size == 1 -> {
                    val parsed = qtyMatches.first().groupValues[1].toLongOrNull() ?: 0L
                    reqQty = parsed.toString()
                    lowConfQty = parsed <= 0L
                    remainder = remainder.replaceFirst(qtyMatches.first().value, " ")
                }
                qtyMatches.size > 1 -> {
                    // Prefer the last plausible stock quantity on the line, flagged for operator check
                    val candidate = qtyMatches.last().groupValues[1].toLongOrNull() ?: 0L
                    reqQty = candidate.toString()
                    lowConfQty = true
                    remainder = remainder.replaceFirst(qtyMatches.last().value, " ")
                }
                else -> {
                    reqQty = ""
                    lowConfQty = true
                }
            }

            val residualName = remainder
                .replace(Regex("[|\\[\\];:,]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(80)

            results += OcrExtractedRow(
                rowId = "ocr_${rowCounter++}_$partNumber",
                partNumber = partNumber,
                location = location,
                productionLine = productionLine,
                requiredQty = reqQty,
                materialName = if (residualName.length >= 3) residualName else "",
                lowConfidencePartNumber = lowConfPart,
                lowConfidenceLocation = lowConfLoc,
                lowConfidenceLine = lowConfLine,
                lowConfidenceQty = lowConfQty
            )
        }

        return results
    }

    private fun matchProductionLine(text: String, knownLines: List<String>): Pair<String, String>? {
        val lower = text.lowercase()
        for (line in knownLines) {
            val idx = lower.indexOf(line.lowercase())
            if (idx >= 0) {
                return line to text.substring(idx, idx + line.length)
            }
        }
        // Fuzzy keyword fallback for partial OCR reads (e.g., "Volvo", "LFT", "CDPO", "Nissan")
        for (line in knownLines) {
            val tokens = line.split(" ").filter { it.length >= 3 }
            for (token in tokens) {
                val regex = Regex("\\b${Regex.escape(token)}\\b", RegexOption.IGNORE_CASE)
                val match = regex.find(text)
                if (match != null) {
                    return line to match.value
                }
            }
        }
        return null
    }

    fun isValidRowForCommit(row: OcrExtractedRow): Boolean {
        val qty = row.requiredQty.toLongOrNull() ?: -1L
        return Material.isValidPartNumber(row.partNumber.trim()) &&
            row.location.isNotBlank() &&
            row.productionLine.isNotBlank() &&
            qty >= 0L
    }
}
