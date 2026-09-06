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
    /** Binaural level (settings-controlled, outside BAL): 0f..1f. */
    val binauralLevel: Float = 0.4f,
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

/**
 * Binaural-beat presets (carrier + beat Hz). Custom carrier/beat come with the
 * settings screen later in Phase 5.
 */
enum class BinauralPreset(val label: String, val carrierHz: Float, val beatHz: Float) {
    OFF("Off", 0f, 0f),
    SLEEP("Sleep · 3 Hz delta", 180f, 3f),
    MEDITATE("Meditate · 4.5 Hz theta", 190f, 4.5f),
    RELAX("Relax · 6 Hz theta", 200f, 6f),
    FOCUS("Focus · 10 Hz alpha", 220f, 10f),
}

/**
 * On/off + configuration for the two ambient channels (B noise, C binaural).
 * Separate from [MixerState] because these are discrete choices, not knob
 * positions.
 */
data class AmbientState(
    val noiseEnabled: Boolean = false,
    val noiseColor: NoiseColor = NoiseColor.WHITE,
    val binaural: BinauralPreset = BinauralPreset.OFF,
) {
    val binauralEnabled: Boolean get() = binaural != BinauralPreset.OFF
}
