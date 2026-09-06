package org.dylanjones.sleepradio.core.audio

import kotlin.math.cos
import kotlin.math.sin

/**
 * The three parallel audio channels. See RETROSYNC_PLAN.md section 2.
 *
 *  - [MAIN]     local album / local audiobook / internet radio (Media3)
 *  - [NOISE]    coloured noise generator (AudioTrack PCM)
 *  - [BINAURAL] two detuned sine oscillators (AudioTrack PCM)
 */
enum class AudioChannel { MAIN, NOISE, BINAURAL }

/**
 * Per-channel gain bus. There is no software mixing in v1 — each channel is its
 * own player/generator and the OS sums the streams; this type only computes the
 * gain that should be applied to each.
 *
 *   VOL knob  -> [masterGain]     scales all three channels
 *   BAL knob  -> [crossfade]      equal-power balance, MAIN <-> NOISE only
 *   Settings  -> [binauralLevel]  fixed background level, untouched by BAL
 */
data class MixerState(
    /** VOL: 0f..1f master volume of the combined mix. */
    val masterGain: Float = 0.8f,
    /** BAL: 0f = MAIN only, 0.5f = both, 1f = NOISE only. */
    val crossfade: Float = 0.5f,
    /** Binaural level from Settings: 0f..1f. */
    val binauralLevel: Float = 0f,
) {
    private val theta: Float
        get() = crossfade.coerceIn(0f, 1f) * (Math.PI.toFloat() / 2f)

    /** Effective linear gain (0f..1f) to apply to [channel] right now. */
    fun effectiveGain(channel: AudioChannel): Float = when (channel) {
        AudioChannel.MAIN -> cos(theta) * masterGain
        AudioChannel.NOISE -> sin(theta) * masterGain
        AudioChannel.BINAURAL -> binauralLevel.coerceIn(0f, 1f) * masterGain
    }
}
