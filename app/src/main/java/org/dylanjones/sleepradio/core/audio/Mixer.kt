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
 *   BAL knob  -> [crossfade]      tapered, equal-power balance, MAIN <-> NOISE only
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
        get() = taper(crossfade.coerceIn(0f, 1f)) * (Math.PI.toFloat() / 2f)

    /** Effective linear gain (0f..1f) to apply to [channel] right now. */
    fun effectiveGain(channel: AudioChannel): Float = when (channel) {
        AudioChannel.MAIN -> cos(theta) * masterGain
        AudioChannel.NOISE -> sin(theta) * masterGain
        AudioChannel.BINAURAL -> binauralLevel.coerceIn(0f, 1f) * masterGain
    }

    private companion object {
        /**
         * Perceptual taper for the BAL knob. [RotaryKnob] maps physical drag
         * distance straight to `crossfade` (0..1), so without this the knob's
         * travel is linear in `crossfade` — and since hearing is roughly
         * logarithmic, that leaves almost no usable travel near each extreme
         * (MAIN-only / NOISE-only), where the fading-in channel is quiet and a
         * tiny nudge produces a big perceived loudness jump.
         *
         * This eases `crossfade` so it changes slowly near 0 and 1 and fastest
         * through the middle — more knob travel spent where the fading-in
         * channel is quiet, mimicking an audio-taper (log) pot — while leaving
         * 0, 0.5 and 1 exactly where they were (silent, centred, silent).
         *
         * It's the N=4 member of Perlin's general smoothstep family: flat
         * first *three* derivatives at each end (vs. two for the more common
         * smootherstep), so it eases off harder right at the extremes and
         * sweeps through the middle faster. If it still needs more bottom-end
         * resolution, compose it again (`taper(taper(t))`) rather than hand-
         * deriving a higher order — each pass pushes further the same way.
         */
        fun taper(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return x * x * x * x * (x * (x * (x * -20f + 70f) - 84f) + 35f)
        }
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

/**
 * A recallable snapshot of the ambient mix — [AmbientState] plus the binaural
 * level (which lives on [MixerState]). Used for restore-on-launch and the
 * PATTERN slots.
 */
data class AmbientPattern(
    val noiseEnabled: Boolean,
    val noiseColor: NoiseColor,
    val binaural: BinauralPreset,
    val binauralLevel: Float,
) {
    val isSilent: Boolean get() = !noiseEnabled && binaural == BinauralPreset.OFF

    fun summary(): String {
        val parts = buildList {
            if (noiseEnabled) add(noiseColor.name.pretty())
            if (binaural != BinauralPreset.OFF) add(binaural.name.pretty() + " beats")
        }
        return parts.joinToString(" · ").ifEmpty { "Silent" }
    }

    private fun String.pretty() =
        lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
