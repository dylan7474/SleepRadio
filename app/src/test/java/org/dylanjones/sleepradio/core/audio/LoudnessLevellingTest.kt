package org.dylanjones.sleepradio.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12 — the pure maths behind Broadcast loudness levelling:
 * [rmsToGain] (measurement → per-item gain) and [limitSample] (the sink stage's
 * gain + soft limiter).
 */
class LoudnessLevellingTest {

    // --- rmsToGain -----------------------------------------------------------

    @Test
    fun `a hot master is attenuated`() {
        val g = rmsToGain(0.30)
        assertEquals((LOUDNESS_TARGET_RMS / 0.30).toFloat(), g, 1e-4f)
        assertTrue("expected < 1", g < 1f)
    }

    @Test
    fun `a track already at target is left alone`() {
        assertEquals(1f, rmsToGain(LOUDNESS_TARGET_RMS), 1e-3f)
    }

    @Test
    fun `a quiet track is boosted`() {
        val g = rmsToGain(0.03)
        assertEquals((LOUDNESS_TARGET_RMS / 0.03).toFloat(), g, 1e-4f)
        assertTrue("expected > 1", g > 1f)
    }

    @Test
    fun `boost is capped`() {
        assertEquals(MixerController.MAX_ITEM_GAIN, rmsToGain(0.005), 1e-4f)
    }

    @Test
    fun `attenuation is capped`() {
        assertEquals(MixerController.MIN_ITEM_GAIN, rmsToGain(0.9), 1e-4f)
    }

    @Test
    fun `effective silence is not amplified`() {
        assertEquals(1f, rmsToGain(1e-6), 0f)
        assertEquals(1f, rmsToGain(0.0), 0f)
    }

    // --- limitSample -------------------------------------------------------

    @Test
    fun `unity gain is identity`() {
        assertEquals(1000, limitSample(1000, 1f))
        assertEquals(-1000, limitSample(-1000, 1f))
        assertEquals(0, limitSample(0, 1f))
    }

    @Test
    fun `attenuation is linear`() {
        assertEquals(5000, limitSample(10_000, 0.5f))
        assertEquals(-2500, limitSample(-10_000, 0.25f))
    }

    @Test
    fun `a boost below the knee is linear`() {
        assertEquals(20_000, limitSample(10_000, 2f))
    }

    @Test
    fun `a boost past the knee is compressed, not clipped`() {
        val knee = (GAIN_LIMIT_KNEE * 32767f).toInt()
        val out = limitSample(20_000, 2f) // 40_000 before limiting
        assertTrue("above the knee", out > knee)
        assertTrue("below full scale", out < 32767)
        assertTrue("below the raw value", out < 40_000)
    }

    @Test
    fun `extreme input stays in 16-bit range and keeps its sign`() {
        assertTrue(limitSample(32_767, 4f) in 1..32_767)
        assertTrue(limitSample(-32_768, 4f) in -32_768..-1)
    }
}
