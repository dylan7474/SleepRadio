package org.dylanjones.sleepradio.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Phase 13A — the broadcast-voice EQ biquads and the assembled curve. */
class VoiceEqTest {

    private val fs = 48_000

    /** Steady-state gain (out RMS / in RMS) of [biquad] for a sine at [freqHz]. */
    private fun gainAt(biquad: Biquad, freqHz: Double, n: Int = 16_384): Double {
        val w = 2.0 * Math.PI * freqHz / fs
        var sumIn = 0.0
        var sumOut = 0.0
        for (i in 0 until n) {
            val x = sin(w * i)
            val y = biquad.process(x)
            if (i >= n / 2) { // second half only — let the filter settle
                sumIn += x * x
                sumOut += y * y
            }
        }
        return sqrt(sumOut / sumIn)
    }

    private fun db(ratio: Double) = 20.0 * log10(ratio)

    /** Gain of the whole [VoiceEq] curve for a sine tone at [freqHz]. */
    private fun curveGainAt(freqHz: Double, n: Int = 16_384): Double {
        val w = 2.0 * Math.PI * freqHz / fs
        val buf = FloatArray(n) { sin(w * it).toFloat() }
        VoiceEq.process(buf, fs)
        var sumIn = 0.0
        var sumOut = 0.0
        for (i in n / 2 until n) {
            val x = sin(w * i)
            sumIn += x * x
            sumOut += buf[i].toDouble() * buf[i]
        }
        return sqrt(sumOut / sumIn)
    }

    // --- individual biquads ------------------------------------------------

    @Test
    fun `high-pass removes DC`() {
        val hp = hpf(120.0, 0.707, fs)
        var y = 0.0
        repeat(8_000) { y = hp.process(1.0) }
        assertEquals(0.0, y, 1e-3)
    }

    @Test
    fun `high-pass passes the top end flat`() {
        assertTrue("|gain| under 0.7 dB at 6 kHz", kotlin.math.abs(db(gainAt(hpf(120.0, 0.707, fs), 6_000.0))) < 0.7)
    }

    @Test
    fun `high-pass rolls off well below the corner`() {
        assertTrue("at least -10 dB at 40 Hz", db(gainAt(hpf(120.0, 0.707, fs), 40.0)) < -10.0)
    }

    @Test
    fun `peaking boost hits its target at centre`() {
        assertEquals(4.0, db(gainAt(peaking(3_500.0, 4.0, 1.0, fs), 3_500.0)), 0.6)
    }

    @Test
    fun `peaking cut hits its target at centre`() {
        assertEquals(-5.0, db(gainAt(peaking(250.0, -5.0, 1.0, fs), 250.0)), 0.6)
    }

    @Test
    fun `peaking is flat far from its centre`() {
        assertTrue(kotlin.math.abs(db(gainAt(peaking(250.0, -5.0, 1.0, fs), 4_000.0))) < 0.7)
    }

    @Test
    fun `high shelf lifts the top and leaves the bottom alone`() {
        assertEquals(2.0, db(gainAt(highShelf(8_000.0, 2.0, 0.707, fs), 16_000.0)), 0.7)
        assertTrue(kotlin.math.abs(db(gainAt(highShelf(8_000.0, 2.0, 0.707, fs), 150.0))) < 0.5)
    }

    // --- assembled curve -------------------------------------------------

    @Test
    fun `curve lifts presence well above the boomy low-mid`() {
        val lowMid = db(curveGainAt(250.0))
        val presence = db(curveGainAt(3_500.0))
        assertTrue("low-mid is cut", lowMid < -2.0)
        assertTrue("presence is boosted", presence > 2.0)
        assertTrue("presence sits >= 6 dB above low-mid", presence - lowMid >= 6.0)
    }

    @Test
    fun `curve strongly attenuates rumble`() {
        assertTrue("at least -10 dB at 40 Hz", db(curveGainAt(40.0)) < -10.0)
    }

    @Test
    fun `process is finite and tolerates degenerate input`() {
        val buf = FloatArray(4_096) { Random(7).nextFloat() * 2f - 1f }
        VoiceEq.process(buf, fs)
        assertTrue(buf.all { it.isFinite() })
        VoiceEq.process(FloatArray(0), fs) // no throw
        VoiceEq.process(FloatArray(64) { 0.1f }, 0) // bad rate: no throw
    }
}
