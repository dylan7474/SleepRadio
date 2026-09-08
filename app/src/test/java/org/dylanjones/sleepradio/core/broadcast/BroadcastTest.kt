package org.dylanjones.sleepradio.core.broadcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import kotlin.random.Random

class BroadcastSelectorTest {

    private fun pool(n: Int, artists: Int = n) = List(n) { i ->
        BroadcastTrack("u$i", "Track $i", "Artist ${i % artists}", "Album")
    }

    @Test
    fun `empty pool yields null`() {
        assertNull(BroadcastSelector(emptyList()).next())
    }

    @Test
    fun `never repeats a track within the no-repeat window`() {
        val sel = BroadcastSelector(pool(20), rng = Random(1))
        val window = 10 // pool/2, capped at 20
        val seen = ArrayDeque<String>()
        repeat(200) {
            val t = sel.next()!!
            assertTrue("repeat of ${t.uri} inside window: $seen", t.uri !in seen)
            seen.addLast(t.uri)
            while (seen.size > window) seen.removeFirst()
        }
    }

    @Test
    fun `spaces the same artist apart`() {
        val sel = BroadcastSelector(pool(30, artists = 6), rng = Random(7))
        var prev: String? = null
        var prevPrev: String? = null
        repeat(150) {
            val a = sel.next()!!.artist
            assertNotEquals(prev, a)
            assertNotEquals(prevPrev, a)
            prevPrev = prev
            prev = a
        }
    }

    @Test
    fun `same seed is deterministic`() {
        val a = BroadcastSelector(pool(12), rng = Random(42))
        val b = BroadcastSelector(pool(12), rng = Random(42))
        repeat(50) { assertEquals(a.next(), b.next()) }
    }

    @Test
    fun `tiny pool still cycles without crashing`() {
        val sel = BroadcastSelector(pool(2), rng = Random(0))
        repeat(20) { assertTrue(sel.next() != null) }
    }
}

class ShowClockTest {

    private val noon = LocalTime.of(13, 0)   // outside the 06–10 morning window
    private val N = WindDownPhase.NORMAL

    @Test
    fun `link every N tracks, ident on the Mth link`() {
        val clock = ShowClock(BroadcastConfig(tracksPerLink = 3, linksPerIdent = 3, linksPerTimeCheck = 4))
        val kinds = List(30) { clock.onTrackStarted(noon, N) }
        assertEquals(LinkKind.NONE, kinds[0])
        assertEquals(LinkKind.NONE, kinds[1])
        assertEquals(LinkKind.LINK, kinds[2])
        assertEquals(LinkKind.NONE, kinds[3])
        assertEquals(LinkKind.LINK, kinds[5])   // 2nd link
        assertEquals(LinkKind.IDENT, kinds[8])  // 3rd link -> ident
    }

    @Test
    fun `reset restarts the cadence`() {
        val clock = ShowClock(BroadcastConfig(tracksPerLink = 2))
        clock.onTrackStarted(noon, N)
        clock.reset()
        assertEquals(LinkKind.NONE, clock.onTrackStarted(noon, N))
        assertEquals(LinkKind.LINK, clock.onTrackStarted(noon, N))
    }

    @Test
    fun `SILENT phase never speaks`() {
        val clock = ShowClock(BroadcastConfig(tracksPerLink = 1))
        repeat(20) {
            assertEquals(LinkKind.NONE, clock.onTrackStarted(noon, WindDownPhase.SILENT))
        }
    }

    @Test
    fun `EASING doubles the gap and only ever LINKs`() {
        val clock = ShowClock(BroadcastConfig(tracksPerLink = 2, linksPerIdent = 2, linksPerTimeCheck = 2))
        val kinds = List(16) { clock.onTrackStarted(noon, WindDownPhase.EASING) }
        // gap is 2*2 = 4, so links land on index 3, 7, 11, 15 — all LINK, no IDENT/TIME_CHECK
        assertEquals(LinkKind.NONE, kinds[2])
        assertEquals(LinkKind.LINK, kinds[3])
        assertEquals(LinkKind.LINK, kinds[7])
        assertTrue(kinds.none { it == LinkKind.IDENT || it == LinkKind.TIME_CHECK })
    }

    @Test
    fun `mornings get more time checks`() {
        val morning = LocalTime.of(8, 0)
        val clock = ShowClock(BroadcastConfig(tracksPerLink = 1, linksPerIdent = 99, linksPerTimeCheck = 4))
        val kinds = List(8) { clock.onTrackStarted(morning, WindDownPhase.NORMAL) }
        // linksPerTimeCheck halves to 2 in the morning -> every 2nd link is a time check
        assertEquals(LinkKind.TIME_CHECK, kinds[1])
        assertEquals(LinkKind.TIME_CHECK, kinds[3])
    }

    @Test
    fun `maximum chattiness links every track and never bare idents`() {
        val clock = ShowClock(
            BroadcastConfig(
                tracksPerLink = 1,
                linksPerIdent = 3,
                linksPerTimeCheck = 4,
                announceEveryTrack = true,
            ),
        )
        val kinds = List(24) { clock.onTrackStarted(noon, N) }
        assertTrue("every gap speaks", kinds.none { it == LinkKind.NONE })
        assertTrue("no bare idents", kinds.none { it == LinkKind.IDENT })
        assertTrue("still checks the time", kinds.any { it == LinkKind.TIME_CHECK })
    }
}

class ChattinessTest {
    @Test fun `maximum maps to a link every track`() {
        assertEquals(Chattiness.MAXIMUM, Chattiness.fromId("maximum"))
        assertEquals(1, Chattiness.MAXIMUM.tracksPerLink)
    }

    @Test fun `unknown id falls back to the default`() {
        assertEquals(Chattiness.DEFAULT, Chattiness.fromId("bogus"))
    }
}

class DjScriptBuilderTest {

    private val t1 = BroadcastTrack("a", "Song One", "The Band", "Album")
    private val t2 = BroadcastTrack("b", "Song Two", "Other Band", "Album")

    @Test
    fun `NONE yields empty`() {
        assertEquals("", DjScriptBuilder().build(LinkKind.NONE, t1, t2))
    }

    @Test
    fun `welcome greets by time of day and names the station`() {
        val b = DjScriptBuilder(Random(0))
        assertTrue(b.welcome(now = LocalTime.of(8, 0)).startsWith("Good morning"))
        assertTrue(b.welcome(now = LocalTime.of(14, 0)).startsWith("Good afternoon"))
        assertTrue(b.welcome(now = LocalTime.of(20, 0)).startsWith("Good evening"))
        assertTrue(b.welcome(now = LocalTime.of(2, 0)).startsWith("Hello"))
        assertTrue(b.welcome(now = LocalTime.of(20, 0)).contains("welcome to Sleep Radio"))
    }

    @Test
    fun `welcome announces the first track when given one`() {
        val s = DjScriptBuilder(Random(0)).welcome(first = t1, now = LocalTime.of(20, 0))
        assertTrue(s, s.contains("welcome to Sleep Radio"))
        assertTrue(s, s.contains("Song One") && s.contains("The Band"))
    }

    @Test
    fun `welcome greeting and first-track lines split for an opening jingle`() {
        val b = DjScriptBuilder(Random(0))
        val greeting = b.welcomeGreeting(LocalTime.of(20, 0))
        val firstTrack = b.welcomeFirstTrack(t1)
        assertTrue(greeting, greeting.contains("Good evening") && greeting.contains("Sleep Radio"))
        assertTrue(greeting, !greeting.contains("Song One")) // no track name in the greeting half
        assertTrue(greeting, greeting.endsWith("."))
        assertTrue(firstTrack, firstTrack.contains("Song One") && firstTrack.contains("The Band"))
        assertTrue(firstTrack, firstTrack.endsWith("."))
    }

    @Test
    fun `link names both tracks`() {
        val s = DjScriptBuilder(Random(0)).build(LinkKind.LINK, t1, t2)
        assertTrue(s, s.contains("Song One") && s.contains("Song Two"))
        assertTrue(s, s.contains("The Band") && s.contains("Other Band"))
    }

    @Test
    fun `outro and intro lines split the link for a jingle segue`() {
        val b = DjScriptBuilder(Random(0))
        val outro = b.outroLine(t1)
        val intro = b.introLine(t2)
        // outro names only the finished track, intro only the next
        assertTrue(outro, outro.contains("Song One") && !outro.contains("Song Two"))
        assertTrue(intro, intro.contains("Song Two") && !intro.contains("Song One"))
        // each is a complete sentence
        assertTrue(outro, outro.endsWith("."))
        assertTrue(intro, intro.endsWith("."))
        assertTrue(outro, !outro.contains("Before that"))
    }

    @Test
    fun `time line is the clock alone, no track intro`() {
        val s = DjScriptBuilder(Random(0)).timeLine(LocalTime.of(19, 31))
        assertTrue(s, s.contains("half past seven"))
        assertTrue(s, !s.contains("Here's") && !s.contains("Song"))
        assertTrue(s, s.none { it.isDigit() })
    }

    @Test
    fun `outro and intro fall back to a filler line when a track is missing`() {
        val b = DjScriptBuilder(Random(0))
        // No track to name, but never an empty utterance or a dangling "by".
        for (line in listOf(b.outroLine(null), b.introLine(null))) {
            assertTrue(line, line.isNotBlank() && line.endsWith("."))
            assertTrue(line, !line.contains(", by "))
        }
    }

    @Test
    fun `link back-announces then introduces, no dangling phrases`() {
        repeat(40) {
            val s = DjScriptBuilder(Random(it.toLong())).build(LinkKind.LINK, t1, t2)
            // finished track named first, next track after it
            assertTrue(s, s.indexOf("Song One") < s.indexOf("Song Two"))
            // opens with a real back-announce, not "Before that," (no antecedent)
            assertTrue(s, Regex("^(That was|You just heard|We just heard) ").containsMatchIn(s))
            assertTrue(s, !s.contains("Before that"))
            // never claims we're staying with an artist when the next one differs
            assertTrue(s, !s.contains("Let's stay with"))
        }
    }

    @Test
    fun `artist hidden when it equals the title`() {
        val odd = BroadcastTrack("c", "Raindrops", "Raindrops", "Album")
        val s = DjScriptBuilder(Random(0)).build(LinkKind.LINK, odd, null)
        assertTrue(s, !s.contains("Raindrops, by Raindrops"))
    }

    @Test
    fun `ident is a station line`() {
        val s = DjScriptBuilder(Random(0)).build(LinkKind.IDENT, t1, t2)
        assertTrue(s, s.contains("Sleep Radio"))
    }

    @Test
    fun `terse link is outro only, no next track`() {
        val s = DjScriptBuilder(Random(0)).build(LinkKind.LINK, t1, t2, terse = true)
        assertTrue(s, s.contains("Song One"))
        assertTrue(s, !s.contains("Song Two"))
    }

    @Test
    fun `time check speaks the clock in words`() {
        val s = DjScriptBuilder(Random(0)).build(LinkKind.TIME_CHECK, t1, t2, LocalTime.of(23, 16))
        assertTrue(s, s.contains("quarter past eleven"))
        assertTrue(s, s.none { it.isDigit() })
    }

    @Test
    fun `time check names only the next track by default`() {
        val s = DjScriptBuilder(Random(0)).build(LinkKind.TIME_CHECK, t1, t2, LocalTime.of(23, 16))
        assertTrue(s, s.contains("Song Two"))
        assertTrue(s, !s.contains("Song One"))
    }

    @Test
    fun `time check back-announces the previous track at maximum chattiness`() {
        val s = DjScriptBuilder(Random(0))
            .build(LinkKind.TIME_CHECK, t1, t2, LocalTime.of(23, 16), announceEveryTrack = true)
        assertTrue(s, s.contains("Song One") && s.contains("Song Two"))
        assertTrue(s, s.contains("quarter past eleven"))
    }
}

class SpokenTimeTest {

    @Test fun midnight() = assertEquals("midnight", spokenTime(LocalTime.of(0, 2)))
    @Test fun midday() = assertEquals("midday", spokenTime(LocalTime.of(12, 1)))
    @Test fun oclock() = assertEquals("nine o'clock", spokenTime(LocalTime.of(9, 0)))
    @Test fun quarterPast() = assertEquals("quarter past three", spokenTime(LocalTime.of(15, 14)))
    @Test fun halfPast() = assertEquals("half past ten", spokenTime(LocalTime.of(22, 31)))
    @Test fun quarterTo() = assertEquals("quarter to eight", spokenTime(LocalTime.of(19, 44)))
    @Test fun pastMinutes() = assertEquals("ten minutes past six", spokenTime(LocalTime.of(18, 9)))
    @Test fun toMinutes() = assertEquals("twenty minutes to five", spokenTime(LocalTime.of(16, 41)))

    @Test
    fun `never contains a digit`() {
        for (h in 0..23) for (m in 0..59) {
            val s = spokenTime(LocalTime.of(h, m))
            assertTrue("$h:$m -> $s", s.none { it.isDigit() })
        }
    }
}
