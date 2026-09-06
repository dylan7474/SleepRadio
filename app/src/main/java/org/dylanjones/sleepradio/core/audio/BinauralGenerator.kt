package org.dylanjones.sleepradio.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Channel C: a binaural-beat generator. Left ear gets a pure sine at the carrier
 * frequency, right ear the carrier + beat frequency; the brain perceives a beat
 * at their difference. Needs headphones.
 *
 * Plain class (no Hilt — same reason as [NoiseGenerator]); [MixerController]
 * owns and drives it. Public methods are thread-safe.
 */
class BinauralGenerator {

    private companion object {
        const val TAG = "BinauralGenerator"
        const val SAMPLE_RATE = 48_000
        const val FRAMES_PER_BUFFER = 1024
        const val GAIN_SMOOTHING = 0.0005f
        /** Headroom so the two tones never clip when summed with other channels. */
        const val HEADROOM = 0.6f
    }

    @Volatile private var targetGain = 0f
    @Volatile private var carrierHz = 200f
    @Volatile private var beatHz = 4f
    @Volatile private var running = false
    private var thread: Thread? = null

    /** Linear output gain 0f..1f (already master-scaled by the caller). */
    fun setGain(value: Float) {
        targetGain = value.coerceIn(0f, 1f)
    }

    fun setTones(carrier: Float, beat: Float) {
        carrierHz = carrier.coerceIn(50f, 500f)
        beatHz = beat.coerceIn(0.5f, 40f)
    }

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "binaural-gen").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

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
                .setBufferSizeInBytes(bufFrames * 2 * Float.SIZE_BYTES * 4)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed", e)
            running = false
            return
        }

        val out = FloatArray(bufFrames * 2)
        var gain = 0f
        var phaseL = 0.0
        var phaseR = 0.0
        val twoPi = 2.0 * PI
        track.play()
        try {
            while (running && !Thread.currentThread().isInterrupted) {
                val target = targetGain
                val incL = twoPi * carrierHz / SAMPLE_RATE
                val incR = twoPi * (carrierHz + beatHz) / SAMPLE_RATE
                var i = 0
                while (i < out.size) {
                    gain += (target - gain) * GAIN_SMOOTHING
                    val g = gain * HEADROOM
                    out[i] = (sin(phaseL) * g).toFloat()
                    out[i + 1] = (sin(phaseR) * g).toFloat()
                    phaseL += incL
                    if (phaseL >= twoPi) phaseL -= twoPi
                    phaseR += incR
                    if (phaseR >= twoPi) phaseR -= twoPi
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
}
