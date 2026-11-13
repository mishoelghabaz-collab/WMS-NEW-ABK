package com.example.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrMaterialParserTest {

    @Test
    fun parseTextLines_extractsExact8DigitPartLocationLineAndQty() {
        val rawLines = listOf(
            "13007859 A0101 Volvo LFT 120",
            "24001122 C0402 CDPO 45",
            "35009988 F0501 Nissan 300"
        )

        val rows = OcrMaterialParser.parseTextLines(rawLines)

        assertEquals(3, rows.size)

        assertEquals("13007859", rows[0].partNumber)
        assertEquals("A0101", rows[0].location)
        assertEquals("Volvo LFT", rows[0].productionLine)
        assertEquals("120", rows[0].requiredQty)
        assertFalse(rows[0].hasLowConfidence)
        assertTrue(OcrMaterialParser.isValidRowForCommit(rows[0]))

        assertEquals("24001122", rows[1].partNumber)
        assertEquals("C0402", rows[1].location)
        assertEquals("CDPO", rows[1].productionLine)
        assertEquals("45", rows[1].requiredQty)
        assertFalse(rows[1].hasLowConfidence)

        assertEquals("35009988", rows[2].partNumber)
        assertEquals("F0501", rows[2].location)
        assertEquals("Nissan", rows[2].productionLine)
        assertEquals("300", rows[2].requiredQty)
        assertFalse(rows[2].hasLowConfidence)
    }

    @Test
    fun parseTextLines_flagsLowConfidenceForSpacedDigitsOrMissingFields() {
        val rawLines = listOf(
            "1300 7859 MissingLoc 80",
            "99887766 A-02-04"
        )

        val rows = OcrMaterialParser.parseTextLines(
            rawLines = rawLines,
            knownLines = listOf("Volvo LFT", "CDPO")
        )

        assertEquals(2, rows.size)

        assertEquals("13007859", rows[0].partNumber)
        assertTrue(rows[0].lowConfidencePartNumber)
        assertTrue(rows[0].lowConfidenceLocation)
        assertTrue(rows[0].lowConfidenceLine)
        assertEquals("80", rows[0].requiredQty)
        assertFalse(OcrMaterialParser.isValidRowForCommit(rows[0]))

        assertEquals("99887766", rows[1].partNumber)
        assertEquals("A-02-04", rows[1].location)
        assertFalse(rows[1].lowConfidenceLocation)
        assertTrue(rows[1].lowConfidenceLine)
        assertTrue(rows[1].lowConfidenceQty)
    }
}
