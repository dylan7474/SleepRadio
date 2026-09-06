package org.dylanjones.sleepradio.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class MixerStateTest {

    @Test
    fun `crossfade fully left = main only`() {
        val s = MixerState(masterGain = 1f, crossfade = 0f)
        assertEquals(1f, s.effectiveGain(AudioChannel.MAIN), 1e-4f)
        assertEquals(0f, s.effectiveGain(AudioChannel.NOISE), 1e-4f)
    }

    @Test
    fun `crossfade fully right = noise only`() {
        val s = MixerState(masterGain = 1f, crossfade = 1f)
        assertEquals(0f, s.effectiveGain(AudioChannel.MAIN), 1e-4f)
        assertEquals(1f, s.effectiveGain(AudioChannel.NOISE), 1e-4f)
    }

    @Test
    fun `crossfade centre is equal-power (~0_707 each)`() {
        val s = MixerState(masterGain = 1f, crossfade = 0.5f)
        val main = s.effectiveGain(AudioChannel.MAIN)
        val noise = s.effectiveGain(AudioChannel.NOISE)
        assertEquals(sqrt(0.5f), main, 1e-3f)
        assertEquals(sqrt(0.5f), noise, 1e-3f)
    }

    @Test
    fun `crossfade keeps constant power across the sweep`() {
        // main^2 + noise^2 should stay ~= masterGain^2 at every position.
        for (i in 0..20) {
            val s = MixerState(masterGain = 0.8f, crossfade = i / 20f)
            val power = s.effectiveGain(AudioChannel.MAIN).let { it * it } +
                s.effectiveGain(AudioChannel.NOISE).let { it * it }
            assertEquals(0.64f, power, 1e-3f)
        }
    }

    @Test
    fun `master gain scales all channels`() {
        val s = MixerState(masterGain = 0.5f, crossfade = 0.5f, binauralLevel = 1f)
        assertEquals(0.5f, s.effectiveGain(AudioChannel.BINAURAL), 1e-4f)
        assertEquals(sqrt(0.5f) * 0.5f, s.effectiveGain(AudioChannel.MAIN), 1e-3f)
    }

    @Test
    fun `binaural sits outside the crossfade`() {
        val left = MixerState(masterGain = 1f, crossfade = 0f, binauralLevel = 0.4f)
        val right = MixerState(masterGain = 1f, crossfade = 1f, binauralLevel = 0.4f)
        assertEquals(
            left.effectiveGain(AudioChannel.BINAURAL),
            right.effectiveGain(AudioChannel.BINAURAL),
            1e-4f,
        )
        assertEquals(0.4f, left.effectiveGain(AudioChannel.BINAURAL), 1e-4f)
    }
}
