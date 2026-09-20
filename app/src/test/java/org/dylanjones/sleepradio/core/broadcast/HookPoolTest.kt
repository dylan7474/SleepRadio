package org.dylanjones.sleepradio.core.broadcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class HookPoolTest {

    private val t1 = BroadcastTrack("u1", "Song One", "The Band", "Album")
    private val t2 = BroadcastTrack("u2", "Song Two", "Other Band", "Album")

    @Test
    fun `empty pool yields null`() {
        assertNull(HookPool(emptyList()).next())
    }

    @Test
    fun `uses every hook once before repeating`() {
        val hooks = List(8) { "Hook $it" }
        val pool = HookPool(hooks, Random(3))
        repeat(3) {
            val round = List(hooks.size) { pool.next()!! }
            assertEquals(hooks.toSet(), round.toSet())
        }
    }

    @Test
    fun `never repeats back to back across reshuffles`() {
        val pool = HookPool(List(4) { "Hook $it" }, Random(9))
        val seq = List(200) { pool.next()!! }
        seq.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }

    @Test
    fun `single-hook pool still speaks`() {
        val pool = HookPool(listOf("Only one"))
        assertEquals("Only one", pool.next())
        assertEquals("Only one", pool.next())
    }

    @Test
    fun `parseHooks skips blanks and comments`() {
        assertEquals(listOf("A b.", "C d!"), parseHooks("# note\n\nA b.\n  C d!  \n#x\n"))
    }

    @Test
    fun `plain link opens with a hook then names the next track`() {
        val b = DjScriptBuilder(Random(0), HookPool(listOf("Greetings, pop fans!")))
        val s = b.build(LinkKind.LINK, t1, t2)
        assertTrue(s, s.startsWith("Greetings, pop fans! "))
        assertTrue(s, s.contains("Song Two") && s.contains("Other Band"))
    }

    @Test
    fun `hooks do not touch terse links time checks or idents`() {
        val hooks = HookPool(listOf("HOOK"))
        val b = DjScriptBuilder(Random(0), hooks)
        assertFalse(b.build(LinkKind.LINK, t1, t2, terse = true).contains("HOOK"))
        assertFalse(b.build(LinkKind.TIME_CHECK, t1, t2).contains("HOOK"))
        assertFalse(b.build(LinkKind.IDENT, t1, t2).contains("HOOK"))
    }

    @Test
    fun `bundled asset is clean`() {
        val file = listOf("src/main/assets/dj_hooks_70s.txt", "app/src/main/assets/dj_hooks_70s.txt")
            .map(::File).first { it.exists() }
        val hooks = parseHooks(file.readText())
        assertTrue("expected a healthy pool, got ${hooks.size}", hooks.size >= 80)
        assertEquals("duplicates", hooks.size, hooks.toSet().size)
        hooks.forEach { h ->
            assertFalse("digits (TTS): $h", h.any { it.isDigit() })
            assertFalse("tempo talk: $h", Regex("\\b(tempo|pace)\\b", RegexOption.IGNORE_CASE).containsMatchIn(h))
            assertFalse("clock claim: $h", Regex("half-?past|o'clock", RegexOption.IGNORE_CASE).containsMatchIn(h))
            assertTrue("too long for a hook (${h.split(' ').size} words): $h", h.split(' ').size <= 40)
        }
    }
    private fun bundledText(): String =
        listOf("src/main/assets/dj_hooks_70s.txt", "app/src/main/assets/dj_hooks_70s.txt")
            .map(::File).first { it.exists() }.readText()

    /** Anything a hook must never assert: it could be false for the track, hour, place or listener. */
    private val claim = Regex(
        "\\b(new|latest|today|tonight|tomorrow|yesterday|morning|afternoon|evening|hour|weather|rain|sunshine|" +
            "traffic|commute|request(ed|s)?|switchboard|jingle|chart|charts|number one|smash|hit|hits|" +
            "volume|turn it up|louder|dance|dancing|bop|bopping|wiggle|tempo|pace|" +
            "half-?past|o'clock)\\b",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun `no bundled hook makes a claim that could be false`() {
        parseHooks(bundledText()).forEach { h ->
            val m = claim.find(h)
            assertTrue("claim '${m?.value}' in: $h", m == null)
        }
    }

    @Test
    fun `every bundled hook ends its sentence so the voice pauses before the intro`() {
        parseHooks(bundledText()).forEach { h ->
            assertTrue("no closing punctuation: $h", h.last() in ".!?")
        }
    }

    @Test
    fun `the hushed set is big, calm and short`() {
        val text = bundledText()
        val marker = "# --- Hushed set ---"
        assertTrue("hushed section marker missing", marker in text)
        val hushed = parseHooks(text.substringAfter(marker))
        assertTrue("expected a big hushed set, got ${hushed.size}", hushed.size >= 60)
        hushed.forEach { h ->
            assertFalse("shouting in a hushed hook: $h", '!' in h)
            assertTrue("hushed hook too long (${h.split(' ').size} words): $h", h.split(' ').size <= 25)
            // Nothing evaluated about the tracks themselves.
            assertFalse(
                "judges a track: $h",
                Regex("\\b(belter|stormer|cracker|banger|classic|masterpiece|amazing|fantastic|incredible|massive|smash)\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn(h),
            )
            // British spelling, as the rest of the station.
            assertFalse("US spelling: $h", Regex("\\b(neighbor|color|favorite|gray|realize|center)\\b", RegexOption.IGNORE_CASE).containsMatchIn(h))
        }
    }
}
