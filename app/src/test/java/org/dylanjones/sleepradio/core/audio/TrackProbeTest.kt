package org.dylanjones.sleepradio.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 14 — the pure silence-detection + clip-guard helpers of [TrackProbe]. */
class TrackProbeTest {

    private val loud = 0.05 // window mean-square well above SILENCE_FLOOR^2
    private val quiet = 0.0 // digital black

    // --- soundBounds -----------------------------------------------------

    @Test
    fun `all-silent envelope has no bounds`() {
        assertArrayEquals(longArrayOf(-1L, -1L), soundBounds(DoubleArray(50) { quiet }, WINDOW_MS))
    }

    @Test
    fun `bounds span first to last sound window`() {
        val env = doubleArrayOf(quiet, quiet, loud, loud, loud, quiet, quiet)
        // first sound = index 2 -> 40 ms; last sound = index 4 -> end 100 ms
        assertArrayEquals(longArrayOf(40L, 100L), soundBounds(env, WINDOW_MS))
    }

    @Test
    fun `a lone sound window at the very start`() {
        assertArrayEquals(longArrayOf(0L, 20L), soundBounds(doubleArrayOf(loud, quiet, quiet), WINDOW_MS))
    }

    @Test
    fun `dither below the floor still counts as silence`() {
        val env = DoubleArray(20) { 1e-7 } // ~ -70 dBFS mean-square
        assertArrayEquals(longArrayOf(-1L, -1L), soundBounds(env, WINDOW_MS))
    }

    // --- edgeTrim ------------------------------------------------------

    @Test
    fun `three seconds of trailing black is trimmed, with a decay pad`() {
        val t = edgeTrim(fileEndMs = 180_000, firstSoundMs = 0, lastSoundEndMs = 177_000)
        assertEquals(0L, t[0])
        assertEquals(177_000L + TAIL_PAD_MS, t[1])
    }

    @Test
    fun `a suspiciously long black tail is left alone`() {
        val t = edgeTrim(fileEndMs = 200_000, firstSoundMs = 0, lastSoundEndMs = 160_000)
        assertArrayEquals(longArrayOf(0L, 0L), t) // 40 s > TRIM_MAX_MS
    }

    @Test
    fun `a tiny end gap is not worth clipping`() {
        val t = edgeTrim(fileEndMs = 180_000, firstSoundMs = 0, lastSoundEndMs = 179_800)
        assertEquals(0L, t[1]) // 200 ms < TRIM_MIN_MS
    }

    @Test
    fun `one second of leading silence is trimmed back to just before the sound`() {
        val t = edgeTrim(fileEndMs = 180_000, firstSoundMs = 1_000, lastSoundEndMs = 179_950)
        assertEquals(1_000L - LEAD_PAD_MS, t[0])
        assertEquals(0L, t[1])
    }

    @Test
    fun `a long lead-in is treated as intentional and kept`() {
        val t = edgeTrim(fileEndMs = 180_000, firstSoundMs = 8_000, lastSoundEndMs = 179_950)
        assertEquals(0L, t[0]) // 8 s > LEAD_MAX_MS
    }

    @Test
    fun `both edges trimmed together`() {
        val t = edgeTrim(fileEndMs = 200_000, firstSoundMs = 1_200, lastSoundEndMs = 196_000)
        assertEquals(1_200L - LEAD_PAD_MS, t[0])
        assertEquals(196_000L + TAIL_PAD_MS, t[1])
    }

    @Test
    fun `sound running to the file end is not clipped`() {
        val t = edgeTrim(fileEndMs = 180_000, firstSoundMs = 0, lastSoundEndMs = 180_000)
        assertArrayEquals(longArrayOf(0L, 0L), t)
    }

    @Test
    fun `unknown duration disables the trailing trim`() {
        val t = edgeTrim(fileEndMs = -1, firstSoundMs = 0, lastSoundEndMs = 120_000)
        assertArrayEquals(longArrayOf(0L, 0L), t)
    }

    @Test
    fun `an inverted clip collapses to nothing`() {
        // pathological short file: start pad would land past the computed end
        val t = edgeTrim(fileEndMs = 4_000, firstSoundMs = 3_800, lastSoundEndMs = 3_000)
        assertArrayEquals(longArrayOf(0L, 0L), t)
    }
}
