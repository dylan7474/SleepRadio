package org.dylanjones.sleepradio.core.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the live [MixerState] (VOL / BAL / binaural level) and [AmbientState]
 * (noise / binaural on-off + config), and owns the procedural ambient
 * generators.
 *
 * Players/generators apply [MixerState.effectiveGain] to their own output:
 *  - Channel A (Media3) is driven by [org.dylanjones.sleepradio.playback.PlaybackConnection].
 *  - Channel B (noise) is driven from here — this controller pushes the effective
 *    NOISE gain straight into [NoiseGenerator].
 *  - Channel C (binaural) joins in a later Phase 5 chunk.
 */
@Singleton
class MixerController @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(MixerState())
    val state: StateFlow<MixerState> = _state.asStateFlow()

    private val _ambient = MutableStateFlow(AmbientState())
    val ambient: StateFlow<AmbientState> = _ambient.asStateFlow()

    private val noise = NoiseGenerator()
    private val binaural = BinauralGenerator()

    init {
        // Keep Channels B (noise) and C (binaural) in sync with VOL / BAL and
        // their on-off / preset choices.
        combine(_state, _ambient) { mix, amb -> mix to amb }
            .onEach { (mix, amb) ->
                noise.setColor(amb.noiseColor)
                if (amb.noiseEnabled) {
                    noise.setGain(mix.effectiveGain(AudioChannel.NOISE))
                    noise.start()
                } else {
                    noise.setGain(0f)
                    noise.stop()
                }

                val bp = amb.binaural
                if (bp != BinauralPreset.OFF) {
                    binaural.setTones(bp.carrierHz, bp.beatHz)
                    binaural.setGain(mix.effectiveGain(AudioChannel.BINAURAL))
                    binaural.start()
                } else {
                    binaural.setGain(0f)
                    binaural.stop()
                }
            }
            .launchIn(scope)
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
}
