package org.dylanjones.sleepradio.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

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
}
