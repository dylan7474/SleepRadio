package org.dylanjones.sleepradio.core.audio

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase 13A — a fixed "broadcast voice" EQ curve for the DJ announcer.
 *
 * The user's cloned Piper voice is bass-heavy and loses intelligibility on
 * small / bass-tilted speakers ("doesn't sound good in the car"). This shapes
 * the synthesised speech: drop the rumble, cut the thick low-mid, lift presence
 * for consonant cut-through, a touch of air on top.
 *
 * Applied in [org.dylanjones.sleepradio.core.tts.DjVoicePlayer.toClip] to the
 * float PCM ([kotlin.FloatArray], ~[-1, 1]) **before** the RMS leveller + soft
 * limiter, so loudness stays constant whatever the EQ cuts or boosts and any
 * overshoot is caught downstream. Announcer only — music and jingles never see
 * this (the DJ voice isn't in the ExoPlayer sink).
 *
 * Stateless: [process] builds fresh [Biquad]s per call, so concurrent clips
 * can't share filter state. Cheap — four biquads, once per synthesised clip.
 *
 * Tune points are the four `(_HZ, _DB)` pairs below.
 */
object VoiceEq {

    /** High-pass: kill sub-bass / rumble the DJ voice doesn't need. */
    private const val HPF_HZ = 120.0
    private const val HPF_Q = 0.707

    /** Low-mid bell cut: the "thick / boomy" region — the main de-mud move. */
    private const val LOWMID_HZ = 250.0
    private const val LOWMID_DB = -5.0
    private const val LOWMID_Q = 1.0

    /** Presence bell boost: consonant intelligibility against road noise. */
    private const val PRESENCE_HZ = 3500.0
    private const val PRESENCE_DB = 4.0
    private const val PRESENCE_Q = 1.0

    /** High shelf: a little "air"; ~no-op for the 16 kHz stock voice. */
    private const val AIR_HZ = 8000.0
    private const val AIR_DB = 2.0
    private const val AIR_Q = 0.707

    /** Apply the curve to [samples] in place. No-op for empty / bad input. */
    fun process(samples: FloatArray, sampleRate: Int) {
        if (samples.isEmpty() || sampleRate <= 0) return
        // Keep every centre frequency comfortably below Nyquist (the stock voice
        // is 16 kHz, so AIR_HZ would otherwise sit right on it).
        val ceil = sampleRate * 0.45
        val stages = arrayOf(
            hpf(min(HPF_HZ, ceil), HPF_Q, sampleRate),
            peaking(min(LOWMID_HZ, ceil), LOWMID_DB, LOWMID_Q, sampleRate),
            peaking(min(PRESENCE_HZ, ceil), PRESENCE_DB, PRESENCE_Q, sampleRate),
            highShelf(min(AIR_HZ, ceil), AIR_DB, AIR_Q, sampleRate),
        )
        for (i in samples.indices) {
            var s = samples[i].toDouble()
            for (stage in stages) s = stage.process(s)
            samples[i] = s.toFloat()
        }
    }
}

/**
 * One Direct-Form-I biquad section. Coefficients are pre-normalised by `a0`.
 * Not thread-safe (carries `x1/x2/y1/y2`); make one per use.
 */
internal class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }
}

// --- RBJ "Audio EQ Cookbook" biquad designs (unit-tested) -------------------

internal fun hpf(f0: Double, q: Double, sampleRate: Int): Biquad {
    val w0 = 2.0 * Math.PI * f0 / sampleRate
    val cw = cos(w0)
    val alpha = sin(w0) / (2.0 * q)
    val a0 = 1.0 + alpha
    return Biquad(
        b0 = ((1.0 + cw) / 2.0) / a0,
        b1 = (-(1.0 + cw)) / a0,
        b2 = ((1.0 + cw) / 2.0) / a0,
        a1 = (-2.0 * cw) / a0,
        a2 = (1.0 - alpha) / a0,
    )
}

internal fun peaking(f0: Double, dbGain: Double, q: Double, sampleRate: Int): Biquad {
    val a = Math.pow(10.0, dbGain / 40.0)
    val w0 = 2.0 * Math.PI * f0 / sampleRate
    val cw = cos(w0)
    val alpha = sin(w0) / (2.0 * q)
    val a0 = 1.0 + alpha / a
    return Biquad(
        b0 = (1.0 + alpha * a) / a0,
        b1 = (-2.0 * cw) / a0,
        b2 = (1.0 - alpha * a) / a0,
        a1 = (-2.0 * cw) / a0,
        a2 = (1.0 - alpha / a) / a0,
    )
}

internal fun highShelf(f0: Double, dbGain: Double, q: Double, sampleRate: Int): Biquad {
    val a = Math.pow(10.0, dbGain / 40.0)
    val w0 = 2.0 * Math.PI * f0 / sampleRate
    val cw = cos(w0)
    val alpha = sin(w0) / (2.0 * q)
    val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
    val a0 = (a + 1.0) - (a - 1.0) * cw + twoSqrtAAlpha
    return Biquad(
        b0 = (a * ((a + 1.0) + (a - 1.0) * cw + twoSqrtAAlpha)) / a0,
        b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cw)) / a0,
        b2 = (a * ((a + 1.0) + (a - 1.0) * cw - twoSqrtAAlpha)) / a0,
        a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cw)) / a0,
        a2 = ((a + 1.0) - (a - 1.0) * cw - twoSqrtAAlpha) / a0,
    )
}
