package org.dylanjones.sleepradio.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Phase 16B — the pure signal maths behind mic auto-calibration. */
class VuCalibratorTest {

    private val fs = 16_000

    @Test
    fun `finds a chirp planted at a known lag`() {
        val template = makeChirp(fs)
        val plantedLag = 1_337 // ~84 ms
        val signal = FloatArray(fs) { Random(1).nextFloat() * 0.02f - 0.01f } // quiet noise floor
        for (i in template.indices) signal[plantedLag + i] += template[i] * 0.6f

        val (lag, ncc) = bestLagSamples(template, signal, from = 0, maxLag = fs / 2)
        assertEquals(plantedLag.toDouble(), lag.toDouble(), 2.0)
        assertTrue("strong correlation, was $ncc", ncc > 0.5f)
    }

    @Test
    fun `pure noise gives a weak correlation`() {
        val template = makeChirp(fs)
        val noise = FloatArray(fs) { Random(2).nextFloat() * 0.2f - 0.1f }
        val (_, ncc) = bestLagSamples(template, noise, from = 0, maxLag = fs / 2)
        assertTrue("no chirp present, ncc was $ncc", ncc < 0.3f)
    }

    @Test
    fun `degenerate inputs are rejected`() {
        val t = makeChirp(fs)
        assertEquals(-1, bestLagSamples(t, FloatArray(10), from = 0, maxLag = 100).first)
        assertEquals(-1, bestLagSamples(t, FloatArray(fs), from = -1, maxLag = 100).first)
        assertEquals(-1, bestLagSamples(t, FloatArray(fs), from = 0, maxLag = 0).first)
    }

    @Test
    fun `median and MAD`() {
        assertEquals(120, median(listOf(80, 120, 500)))
        assertEquals(110, median(listOf(80, 100, 120, 500)))
        assertEquals(0, median(emptyList()))
        assertEquals(20, medianAbsDev(listOf(100, 120, 140, 160), med = 130))
    }
}
