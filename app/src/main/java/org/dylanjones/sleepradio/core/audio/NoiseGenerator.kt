package org.dylanjones.sleepradio.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Spectra the noise channel (B) can produce. */
enum class NoiseColor { WHITE, PINK, BROWN, BLUE, DEEP_SPACE, AMBIENT }

/**
 * Channel B: a procedural coloured-noise generator. Owns a single streaming
 * [AudioTrack] and a dedicated writer thread — no software mixing, the OS sums
 * this stream with Channel A (Media3) and Channel C (binaural).
 *
 * Not a Hilt type: [MixerController] news it up and drives it. All public
 * methods are safe to call from any thread.
 */
class NoiseGenerator {

    private companion object {
        const val TAG = "NoiseGenerator"
        const val SAMPLE_RATE = 48_000
        /** ~21 ms per write at 48 kHz stereo — low latency, cheap CPU. */
        const val FRAMES_PER_BUFFER = 1024
        /** Per-sample gain smoothing toward the target (one-pole, ~5 ms). */
        const val GAIN_SMOOTHING = 0.0005f
    }

    @Volatile private var targetGain: Float = 0f
    @Volatile private var color: NoiseColor = NoiseColor.WHITE
    @Volatile private var running = false
    private var thread: Thread? = null

    /** Linear output gain 0f..1f (already master- and BAL-scaled by the caller). */
    fun setGain(value: Float) {
        targetGain = value.coerceIn(0f, 1f)
    }

    fun setColor(value: NoiseColor) {
        color = value
    }

    /** Idempotent. Starts the writer thread and begins producing audio. */
    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "noise-gen").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /** Idempotent. Stops audio and releases the [AudioTrack]. */
    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        thread?.interrupt()
        thread = null
    }

    val isRunning: Boolean get() = running

    private fun runLoop() {
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val bufFrames = max(FRAMES_PER_BUFFER, minBytes / (2 * Float.SIZE_BYTES))
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                // Roomy buffer: this is steady background audio, underrun
                // resistance matters far more than latency.
                .setBufferSizeInBytes(bufFrames * 2 * Float.SIZE_BYTES * 4)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed", e)
            running = false
            return
        }

        val out = FloatArray(bufFrames * 2)
        var gain = 0f
        val state = ColorState()
        track.play()
        try {
            while (running && !Thread.currentThread().isInterrupted) {
                val target = targetGain
                val c = color
                var i = 0
                while (i < out.size) {
                    gain += (target - gain) * GAIN_SMOOTHING
                    val s = state.next(c) * gain
                    out[i] = s
                    out[i + 1] = s
                    i += 2
                }
                val written = track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    Log.e(TAG, "AudioTrack.write error $written")
                    break
                }
            }
        } catch (_: InterruptedException) {
            // stop() requested
        } finally {
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
    }

    /**
     * Per-color filter state + sample generation. One instance per writer loop,
     * so no synchronization needed inside.
     */
    private class ColorState {
        private val rng = Random(System.nanoTime())

        // Pink: Paul Kellet's economy (refined) method.
        private var b0 = 0f
        private var b1 = 0f
        private var b2 = 0f
        private var b3 = 0f
        private var b4 = 0f
        private var b5 = 0f
        private var b6 = 0f

        // Brown / deep-space: leaky integrator.
        private var brown = 0f

        // Blue: differentiator memory.
        private var lastWhite = 0f

        // Ambient: slow amplitude LFO over pink.
        private var lfoPhase = 0f

        private fun white(): Float = rng.nextFloat() * 2f - 1f

        private fun pink(white: Float): Float {
            b0 = 0.99886f * b0 + white * 0.0555179f
            b1 = 0.99332f * b1 + white * 0.0750759f
            b2 = 0.96900f * b2 + white * 0.1538520f
            b3 = 0.86650f * b3 + white * 0.3104856f
            b4 = 0.55000f * b4 + white * 0.5329522f
            b5 = -0.7616f * b5 - white * 0.0168980f
            val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
            b6 = white * 0.115926f
            return pink * 0.11f
        }

        private fun brown(white: Float, leak: Float, scale: Float): Float {
            brown = (brown + leak * white) / (1f + leak)
            return (brown * scale).coerceIn(-1f, 1f)
        }

        fun next(color: NoiseColor): Float {
            val w = white()
            return when (color) {
                NoiseColor.WHITE -> w * 0.5f
                NoiseColor.PINK -> pink(w)
                NoiseColor.BROWN -> brown(w, 0.02f, 3.5f)
                NoiseColor.BLUE -> {
                    val blue = (w - lastWhite) * 0.5f
                    lastWhite = w
                    blue
                }
                NoiseColor.DEEP_SPACE -> brown(w, 0.008f, 4.5f)
                NoiseColor.AMBIENT -> {
                    lfoPhase += 0.10f / SAMPLE_RATE
                    if (lfoPhase > 1f) lfoPhase -= 1f
                    val lfo = 0.75f + 0.25f * sin(lfoPhase * 2f * Math.PI.toFloat())
                    pink(w) * lfo
                }
            }
        }
    }
}
