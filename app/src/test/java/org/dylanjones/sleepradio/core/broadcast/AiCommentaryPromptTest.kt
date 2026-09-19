package org.dylanjones.sleepradio.core.broadcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class BuildDjCommentaryPromptTest {

    private val prev = BroadcastTrack("u1", "Idiot Wind", "Bob Dylan", "Blood on the Tracks")
    private val next = BroadcastTrack("u2", "Let It Be", "The Beatles", "Let It Be")

    @Test
    fun `mentions both track titles and artists`() {
        val prompt = buildDjCommentaryPrompt(prev, next, LocalTime.of(2, 0))
        assertTrue(prompt.contains("Idiot Wind"))
        assertTrue(prompt.contains("Bob Dylan"))
        assertTrue(prompt.contains("Let It Be"))
        assertTrue(prompt.contains("The Beatles"))
    }

    @Test
    fun `time of night changes the framing`() {
        val night = buildDjCommentaryPrompt(prev, next, LocalTime.of(3, 0))
        val evening = buildDjCommentaryPrompt(prev, next, LocalTime.of(20, 0))
        assertTrue(night.contains("middle of the night"))
        assertTrue(evening.contains("late evening"))
    }

    @Test
    fun `no previous track omits the outro half`() {
        val prompt = buildDjCommentaryPrompt(null, next, LocalTime.of(1, 0))
        assertTrue(prompt.contains("Let It Be"))
        assertTrue(!prompt.contains("just finished"))
    }

    @Test
    fun `no next track omits the intro half`() {
        val prompt = buildDjCommentaryPrompt(prev, null, LocalTime.of(1, 0))
        assertTrue(prompt.contains("Idiot Wind"))
        assertTrue(!prompt.contains("next song"))
    }

    @Test
    fun `recent history is included as an optional callback`() {
        val prompt = buildDjCommentaryPrompt(prev, next, LocalTime.of(1, 0), listOf("Hey Jude by The Beatles"))
        assertTrue(prompt.contains("Hey Jude by The Beatles"))
        assertTrue(prompt.contains("callback"))
    }

    @Test
    fun `empty history adds nothing extra`() {
        val prompt = buildDjCommentaryPrompt(prev, next, LocalTime.of(1, 0), emptyList())
        assertTrue(!prompt.contains("callback"))
    }
}

class SanitizeAiLineTest {

    @Test
    fun `trims and collapses whitespace`() {
        assertEquals("That was a good one.", sanitizeAiLine("  That   was\n a good  one.  "))
    }

    @Test
    fun `strips one layer of wrapping quotes`() {
        assertEquals("A quoted line.", sanitizeAiLine("\"A quoted line.\""))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(sanitizeAiLine("   "))
        assertNull(sanitizeAiLine(""))
    }

    @Test
    fun `too-short input is rejected`() {
        assertNull(sanitizeAiLine("Hi."))
    }

    @Test
    fun `absurdly long input is rejected`() {
        assertNull(sanitizeAiLine("word ".repeat(100)))
    }

    @Test
    fun `markdown artefacts are rejected`() {
        assertNull(sanitizeAiLine("**That was great.**"))
        assertNull(sanitizeAiLine("# A heading"))
        assertNull(sanitizeAiLine("`code`"))
    }

    @Test
    fun `runs through the speech normalizer`() {
        // 1984 reads as a year via normalizeForSpeech, not digit-by-digit.
        val out = sanitizeAiLine("That track came out in 1984, a good year.")
        assertEquals("That track came out in nineteen eighty-four, a good year.", out)
    }

    @Test
    fun `a clean plain line passes through unchanged`() {
        val line = "That was a warm one to drift off to."
        assertEquals(line, sanitizeAiLine(line))
    }
}
