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

    @Test
    fun `link every N tracks, ident on the Mth link`() {
        val clock = ShowClock(BroadcastConfig(tracksPerLink = 3, linksPerIdent = 3, linksPerTimeCheck = 4))
        val kinds = List(30) { clock.onTrackStarted() }
        // tracks 1,2 -> NONE ; track 3 -> a link ; repeat
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
        clock.onTrackStarted()
        clock.reset()
        assertEquals(LinkKind.NONE, clock.onTrackStarted())
        assertEquals(LinkKind.LINK, clock.onTrackStarted())
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
    fun `link names both tracks`() {
        val s = DjScriptBuilder(Random(0)).build(LinkKind.LINK, t1, t2)
        assertTrue(s, s.contains("Song One") && s.contains("Song Two"))
        assertTrue(s, s.contains("The Band") && s.contains("Other Band"))
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
    fun `time check speaks the clock in words`() {
        val s = DjScriptBuilder(Random(0)).build(LinkKind.TIME_CHECK, t1, t2, LocalTime.of(23, 16))
        assertTrue(s, s.contains("quarter past eleven"))
        assertTrue(s, s.none { it.isDigit() })
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
