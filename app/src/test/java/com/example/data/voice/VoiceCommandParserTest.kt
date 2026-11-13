package com.example.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun `parses English issue command accurately`() {
        val result = VoiceCommandParser.parse("Issue 50 pieces of 13009444")
        assertTrue(result is VoiceParseResult.Success)
        val cmd = (result as VoiceParseResult.Success).command
        assertEquals(VoiceCommandAction.ISSUE, cmd.action)
        assertEquals(50L, cmd.quantity)
        assertEquals("13009444", cmd.partNumber)
    }

    @Test
    fun `parses English return command as RECEIVE`() {
        val result = VoiceCommandParser.parse("Return 10 of 69241577")
        assertTrue(result is VoiceParseResult.Success)
        val cmd = (result as VoiceParseResult.Success).command
        assertEquals(VoiceCommandAction.RECEIVE, cmd.action)
        assertEquals(10L, cmd.quantity)
        assertEquals("69241577", cmd.partNumber)
    }

    @Test
    fun `parses English cycle count command with and without quantity`() {
        val withQty = VoiceCommandParser.parse("Count 25 of 13009444")
        assertTrue(withQty is VoiceParseResult.Success)
        val cmd1 = (withQty as VoiceParseResult.Success).command
        assertEquals(VoiceCommandAction.CYCLE_COUNT, cmd1.action)
        assertEquals(25L, cmd1.quantity)
        assertEquals("13009444", cmd1.partNumber)

        val withoutQty = VoiceCommandParser.parse("Cycle count 13009444")
        assertTrue(withoutQty is VoiceParseResult.Success)
        val cmd2 = (withoutQty as VoiceParseResult.Success).command
        assertEquals(VoiceCommandAction.CYCLE_COUNT, cmd2.action)
        assertNull(cmd2.quantity)
        assertEquals("13009444", cmd2.partNumber)
    }

    @Test
    fun `parses Arabic issue return and add commands`() {
        val issueResult = VoiceCommandParser.parse("اصرف 50 من 13009444")
        assertTrue(issueResult is VoiceParseResult.Success)
        val issueCmd = (issueResult as VoiceParseResult.Success).command
        assertEquals(VoiceCommandAction.ISSUE, issueCmd.action)
        assertEquals(50L, issueCmd.quantity)
        assertEquals("13009444", issueCmd.partNumber)

        val returnResult = VoiceCommandParser.parse("ريتيرن 10 من 69241577")
        assertTrue(returnResult is VoiceParseResult.Success)
        val returnCmd = (returnResult as VoiceParseResult.Success).command
        assertEquals(VoiceCommandAction.RECEIVE, returnCmd.action)
        assertEquals(10L, returnCmd.quantity)
        assertEquals("69241577", returnCmd.partNumber)

        val addResult = VoiceCommandParser.parse("زيادة 20 قطعة رقم 71054141")
        assertTrue(addResult is VoiceParseResult.Success)
        val addCmd = (addResult as VoiceParseResult.Success).command
        assertEquals(VoiceCommandAction.RECEIVE, addCmd.action)
        assertEquals(20L, addCmd.quantity)
        assertEquals("71054141", addCmd.partNumber)
    }

    @Test
    fun `normalizes Arabic-Indic digits and spaced 8-digit part numbers`() {
        val result = VoiceCommandParser.parse("اصرف ٥٠ من ١٣٠٠٩٤٤٤")
        assertTrue(result is VoiceParseResult.Success)
        val cmd = (result as VoiceParseResult.Success).command
        assertEquals(VoiceCommandAction.ISSUE, cmd.action)
        assertEquals(50L, cmd.quantity)
        assertEquals("13009444", cmd.partNumber)

        val spaced = VoiceCommandParser.parse("Issue 12 of 1300 9444")
        assertTrue(spaced is VoiceParseResult.Success)
        assertEquals("13009444", (spaced as VoiceParseResult.Success).command.partNumber)
    }

    @Test
    fun `does not confuse Arabic word عدد inside issue command as cycle count`() {
        val result = VoiceCommandParser.parse("اصرف عدد 50 من 13009444")
        assertTrue(result is VoiceParseResult.Success)
        val cmd = (result as VoiceParseResult.Success).command
        assertEquals(VoiceCommandAction.ISSUE, cmd.action)
        assertEquals(50L, cmd.quantity)
        assertEquals("13009444", cmd.partNumber)
    }

    @Test
    fun `returns structured validation failures`() {
        assertEquals(
            VoiceParseResult.Failure(VoiceParseError.EMPTY_INPUT),
            VoiceCommandParser.parse("   ")
        )
        assertEquals(
            VoiceParseResult.Failure(VoiceParseError.MISSING_ACTION),
            VoiceCommandParser.parse("50 pieces of 13009444")
        )
        assertEquals(
            VoiceParseResult.Failure(VoiceParseError.MISSING_PART_NUMBER),
            VoiceCommandParser.parse("Issue 50 of 1300944")
        )
        assertEquals(
            VoiceParseResult.Failure(VoiceParseError.MISSING_PART_NUMBER),
            VoiceCommandParser.parse("Issue 50 of 130094445")
        )
        assertEquals(
            VoiceParseResult.Failure(VoiceParseError.MISSING_QUANTITY),
            VoiceCommandParser.parse("Issue 13009444")
        )
    }
}
