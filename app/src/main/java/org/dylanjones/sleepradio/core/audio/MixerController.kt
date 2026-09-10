package org.dylanjones.sleepradio.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Peak output level per stereo channel, 0..1. */
data class VuLevels(val left: Float, val right: Float) {
    companion object {
        val SILENT = VuLevels(0f, 0f)
    }
}

/**
 * Holds the live [MixerState] (VOL / BAL / binaural level) and [AmbientState]
 * (noise / binaural on-off + config). Pure state — the players read these:
 *  - Channel A (Media3): [org.dylanjones.sleepradio.playback.PlaybackConnection].
 *  - Channels B/C (noise, binaural): [org.dylanjones.sleepradio.playback.AmbientPlaybackService],
 *    which also owns the generators and its own foreground lifecycle.
 */
@Singleton
class MixerController @Inject constructor() {

    private val _state = MutableStateFlow(MixerState())
    val state: StateFlow<MixerState> = _state.asStateFlow()

    private val _ambient = MutableStateFlow(AmbientState())
    val ambient: StateFlow<AmbientState> = _ambient.asStateFlow()

    /**
     * Per-item loudness-levelling gain for Channel A (Phase 12). 1f = untouched;
     * < 1 tames a hot master, > 1 lifts a quiet one. Set by
     * [org.dylanjones.sleepradio.playback.PlaybackConnection] from a just-in-time
     * decode scan of the track/jingle about to play; consumed by the
     * `GainAudioProcessor` in [org.dylanjones.sleepradio.playback.PlaybackService]'s
     * audio sink (routing it through `Player.volume` instead would clamp any
     * boost to 1.0). Only Broadcast mode ever drives it away from 1f.
     */
    private val _itemGain = MutableStateFlow(1f)
    val itemGain: StateFlow<Float> = _itemGain.asStateFlow()

    fun setItemGain(value: Float) {
        _itemGain.value = value.coerceIn(MIN_ITEM_GAIN, MAX_ITEM_GAIN)
    }

    /**
     * Live Channel-A output level, per stereo channel (0..1 peak). Fed by a
     * sampler in [org.dylanjones.sleepradio.playback.PlaybackService] reading the
     * sink's `GainAudioProcessor`; drives the Studio skin's analogue VU meters.
     * Only the music path — ambient noise / binaural / DJ voice are on their own
     * `AudioTrack`s and never reach here.
     */
    private val _vu = MutableStateFlow(VuLevels.SILENT)
    val vu: StateFlow<VuLevels> = _vu.asStateFlow()

    fun setVu(levels: VuLevels) {
        _vu.value = levels
    }

    /** Latest DJ-voice peak (0..1), folded into the VU meters by the
     *  [org.dylanjones.sleepradio.playback.PlaybackService] sampler so the
     *  announcer moves the needles too. Written from the DJ playback thread. */
    @Volatile private var djPeak = 0f

    fun reportDjPeak(peak: Float) {
        val v = peak.coerceIn(0f, 1f)
        if (v > djPeak) djPeak = v
    }

    /** Read the DJ peak since the last call, then reset. */
    fun takeDjPeak(): Float {
        val v = djPeak
        djPeak = 0f
        return v
    }

    /** VOL knob, 0..1 — master volume of the combined mix. */
    fun setVolume(value: Float) {
        _state.update { it.copy(masterGain = value.coerceIn(0f, 1f)) }
    }

    /** BAL knob, 0..1 — equal-power balance, 0 = MAIN only, 1 = NOISE only. */
    fun setBalance(value: Float) {
        _state.update { it.copy(crossfade = value.coerceIn(0f, 1f)) }
    }

    /** Binaural background level from Settings, 0..1. */
    fun setBinauralLevel(value: Float) {
        _state.update { it.copy(binauralLevel = value.coerceIn(0f, 1f)) }
    }

    /** NOISE tile — toggle Channel B on/off. */
    fun toggleNoise() {
        _ambient.update { it.copy(noiseEnabled = !it.noiseEnabled) }
    }

    fun setNoiseEnabled(enabled: Boolean) {
        _ambient.update { it.copy(noiseEnabled = enabled) }
    }

    /** NOISE colour picker. */
    fun setNoiseColor(color: NoiseColor) {
        _ambient.update { it.copy(noiseColor = color) }
    }

    /** Channel C binaural preset ([BinauralPreset.OFF] = disabled). */
    fun setBinaural(preset: BinauralPreset) {
        _ambient.update { it.copy(binaural = preset) }
    }

    /** The whole ambient mix as a recallable snapshot (for persistence / PATTERNs). */
    fun currentPattern(): AmbientPattern = AmbientPattern(
        noiseEnabled = _ambient.value.noiseEnabled,
        noiseColor = _ambient.value.noiseColor,
        binaural = _ambient.value.binaural,
        binauralLevel = _state.value.binauralLevel,
    )

    /** Apply a saved snapshot (restore-on-launch, PATTERN recall). */
    fun applyPattern(p: AmbientPattern) {
        _state.update { it.copy(binauralLevel = p.binauralLevel.coerceIn(0f, 1f)) }
        _ambient.update {
            it.copy(
                noiseEnabled = p.noiseEnabled,
                noiseColor = p.noiseColor,
                binaural = p.binaural,
            )
        }
    }

    companion object {
        /** Bounds for [itemGain] — shared with [LoudnessProbe]'s clamp. */
        const val MIN_ITEM_GAIN = 0.2f
        const val MAX_ITEM_GAIN = 4f
    }
}
