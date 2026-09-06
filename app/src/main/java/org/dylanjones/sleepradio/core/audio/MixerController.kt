package org.dylanjones.sleepradio.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the live [MixerState] (VOL / BAL / binaural level) and exposes it as a
 * flow. Players observe this and apply [MixerState.effectiveGain] to their own
 * output — see [org.dylanjones.sleepradio.playback.PlaybackConnection].
 *
 * Phase 3: only Channel MAIN is connected. NOISE / BINAURAL join in Phase 5.
 */
@Singleton
class MixerController @Inject constructor() {

    private val _state = MutableStateFlow(MixerState())
    val state: StateFlow<MixerState> = _state.asStateFlow()

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
}
