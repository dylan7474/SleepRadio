package org.dylanjones.sleepradio.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * A single wideband gain stage in the Channel-A ([androidx.media3.exoplayer.ExoPlayer])
 * audio sink. Broadcast loudness levelling (Phase 12) feeds it a per-track /
 * per-jingle gain via [setGain]; a value > 1 lifts a quiet master, which
 * [androidx.media3.common.Player.setVolume] (capped at 1.0) can't do.
 *
 * 16-bit PCM only. Any other encoding makes [onConfigure] return
 * [AudioProcessor.AudioFormat.NOT_SET], so [isActive] is false and ExoPlayer
 * bypasses this stage entirely (levelling silently off — e.g. if a device ever
 * negotiates float output). Gain changes are ramped with an ~80 ms time constant
 * so a track-to-track change doesn't zipper, and a soft `tanh` knee above
 * [LIMIT_KNEE] keeps a boost from hard-clipping. Near unity the buffer is copied
 * straight through with no per-sample maths.
 */
@UnstableApi
class GainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetGain: Float = 1f
    private var currentGain: Float = 1f

    /** Per-sample smoothing coefficient, derived from the configured sample rate. */
    private var smoothK: Float = 1f
    private var channels: Int = 2

    /** Peak output level (0..1) per stereo channel since the last [readLevels].
     *  Written on the audio thread, read by the VU sampler — [Volatile] floats,
     *  so a lost buffer at the read/reset boundary is harmless for a meter. */
    @Volatile private var peakL: Float = 0f
    @Volatile private var peakR: Float = 0f

    /** Set the target Channel-A gain. 1f = unchanged; clamped to sane bounds. */
    fun setGain(gain: Float) {
        targetGain = gain.coerceIn(MixerController.MIN_ITEM_GAIN, MixerController.MAX_ITEM_GAIN)
    }

    /** `[left, right]` peak since the previous call, then reset to 0. */
    fun readLevels(): FloatArray {
        val l = peakL
        val r = peakR
        peakL = 0f
        peakR = 0f
        return floatArrayOf(l, r)
    }

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        smoothK = (1.0 - exp(-1.0 / (SMOOTH_TAU_S * inputAudioFormat.sampleRate)))
            .toFloat().coerceIn(1e-5f, 1f)
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // A seek within the same item shouldn't re-ramp from the old gain.
        currentGain = targetGain
    }

    override fun onReset() {
        targetGain = 1f
        currentGain = 1f
        smoothK = 1f
        peakL = 0f
        peakR = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size).order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val target = targetGain
        val passthrough = nearUnity(currentGain) && nearUnity(target)
        if (passthrough) currentGain = target

        val stereo = channels >= 2
        val end = inputBuffer.limit()
        var i = inputBuffer.position()
        var g = currentGain
        var idx = 0
        var pkL = 0f
        var pkR = 0f
        while (i < end - 1) {
            val out = if (passthrough) {
                inputBuffer.getShort(i).toInt()
            } else {
                g += (target - g) * smoothK
                limitSample(inputBuffer.getShort(i).toInt(), g)
            }
            output.putShort(out.toShort())
            val a = abs(out) / 32768f
            if (stereo && (idx and 1) == 1) { if (a > pkR) pkR = a } else if (a > pkL) pkL = a
            idx++
            i += 2
        }
        if (!passthrough) currentGain = g
        peakL = max(peakL, pkL)
        peakR = max(peakR, if (stereo) pkR else pkL)
        inputBuffer.position(end)
        output.flip()
    }

    private companion object {
        /** Gain-ramp time constant (seconds). */
        const val SMOOTH_TAU_S = 0.08

        const val UNITY_EPS = 1e-3f

        fun nearUnity(g: Float) = g > 1f - UNITY_EPS && g < 1f + UNITY_EPS
    }
}

/** Above this fraction of full scale, a boost is `tanh`-compressed, not clipped. */
internal const val GAIN_LIMIT_KNEE = 0.85f
private const val GAIN_FULL_SCALE = 32767f

/**
 * Apply [gain] to a 16-bit [sample], soft-limiting anything the gain pushes past
 * [GAIN_LIMIT_KNEE] of full scale so a boost compresses instead of clipping.
 * Continuous at the knee (`tanh(0) == 0`). Pure — unit-tested.
 */
internal fun limitSample(sample: Int, gain: Float): Int {
    val x = sample * gain
    val knee = GAIN_LIMIT_KNEE * GAIN_FULL_SCALE
    val mag = if (x < 0f) -x else x
    if (mag <= knee) return x.roundToInt().coerceIn(-32768, 32767)
    val range = GAIN_FULL_SCALE - knee
    val limited = knee + range * tanh((mag - knee) / range)
    val signed = if (x < 0f) -limited else limited
    return signed.roundToInt().coerceIn(-32768, 32767)
}
