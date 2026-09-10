package org.dylanjones.sleepradio.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.dylanjones.sleepradio.core.data.VU_DELAY_MAX_MS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase 16B — measure the audio→VU latency acoustically. Plays a short chirp
 * through the media output every ~0.5 s while recording from the mic; for each
 * chirp, the cross-correlation peak in the recording relative to the moment it
 * was submitted is the output latency (the mic-input path cancels out because it
 * shifts both the "now" mark and the arrival equally). Keeps beeping until the
 * running median settles, then returns it in ms.
 *
 * Caller must hold `RECORD_AUDIO`. Runs on [Dispatchers.Default]; every failure
 * path returns [Progress.Failed], never throws.
 */
class VuCalibrator(private val context: Context) {

    sealed interface Progress {
        data class Measuring(val estimateMs: Int, val spreadMs: Int, val readings: Int) : Progress
        data class Done(val delayMs: Int) : Progress
        data class Failed(val reason: String) : Progress
    }

    suspend fun run(bluetooth: Boolean, onProgress: (Progress) -> Unit) =
        withContext(Dispatchers.Default) {
            val template = makeChirp(SAMPLE_RATE)
            val recorder = openRecorder()
                ?: return@withContext onProgress(Progress.Failed("Microphone is unavailable."))
            val player = openPlayer()
            if (player == null) {
                recorder.release()
                return@withContext onProgress(Progress.Failed("Audio output is unavailable."))
            }

            val rec = FloatArray(SAMPLE_RATE * REC_SECONDS)
            val recPos = AtomicInteger(0)
            val micThread = thread(name = "vu-cal-mic", isDaemon = true) {
                val chunk = ShortArray(READ_FRAMES)
                runCatching { recorder.startRecording() }
                while (!Thread.currentThread().isInterrupted) {
                    val n = recorder.read(chunk, 0, chunk.size)
                    if (n <= 0) break
                    var p = recPos.get()
                    var i = 0
                    while (i < n && p < rec.size) {
                        rec[p++] = chunk[i] / 32768f
                        i++
                    }
                    recPos.set(p)
                    if (p >= rec.size) break
                }
            }

            val deltas = ArrayList<Int>()
            try {
                player.play()
                val chirpS = toShort(template)
                val gapS = ShortArray(SAMPLE_RATE * GAP_MS / 1000) // silence between chirps
                val maxLag = SAMPLE_RATE * (VU_DELAY_MAX_MS + 200) / 1000

                for (iter in 0 until MAX_CHIRPS) {
                    coroutineContext.ensureActive()
                    val emitPos = recPos.get()
                    if (emitPos + maxLag + template.size + gapS.size >= rec.size) break
                    player.write(chirpS, 0, chirpS.size, AudioTrack.WRITE_BLOCKING)
                    player.write(gapS, 0, gapS.size, AudioTrack.WRITE_BLOCKING) // paces ~GAP_MS

                    val span = (recPos.get() - emitPos - template.size).coerceAtMost(maxLag)
                    val (lag, ncc) = bestLagSamples(template, rec, emitPos, span)
                    if (lag >= 0 && ncc >= NCC_MIN) {
                        deltas.add(lag * 1000 / SAMPLE_RATE)
                        val med = median(deltas)
                        val mad = medianAbsDev(deltas, med)
                        onProgress(Progress.Measuring(med, mad, deltas.size))
                        if (deltas.size >= MIN_READINGS && mad <= STABLE_MAD_MS) {
                            onProgress(Progress.Done(med.coerceIn(0, VU_DELAY_MAX_MS)))
                            return@withContext
                        }
                    } else {
                        Log.d(TAG, "chirp $iter: no clear peak (ncc=$ncc)")
                    }
                }

                if (deltas.size >= 4) {
                    onProgress(Progress.Done(median(deltas).coerceIn(0, VU_DELAY_MAX_MS)))
                } else {
                    onProgress(
                        Progress.Failed(
                            "Couldn't hear the beeps clearly. Turn the volume up, quieten the room" +
                                if (bluetooth) ", and hold the phone within ~30 cm of the speaker." else ".",
                        ),
                    )
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                throw kotlinx.coroutines.CancellationException()
            } catch (e: Exception) {
                Log.w(TAG, "calibration failed", e)
                onProgress(Progress.Failed("Calibration failed: ${e.message}"))
            } finally {
                micThread.interrupt()
                runCatching { player.pause(); player.flush(); player.release() }
                runCatching { recorder.stop(); recorder.release() }
            }
        }

    private fun openPlayer(): AudioTrack? = runCatching {
        val min = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 4)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(min)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
    }.getOrNull()

    private fun openRecorder(): AudioRecord? = runCatching {
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 2)
        val source = runCatching { MediaRecorder.AudioSource.UNPROCESSED }
            .getOrDefault(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        @Suppress("MissingPermission")
        AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(min * 2)
            .build()
            .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    }.getOrNull()

    private fun toShort(f: FloatArray) = ShortArray(f.size) { (f[it] * 32767f).toInt().toShort() }

    private companion object {
        const val TAG = "VuCalibrator"
        const val SAMPLE_RATE = 16_000
        const val READ_FRAMES = 256
        const val REC_SECONDS = 16
        const val GAP_MS = 450
        const val MAX_CHIRPS = 20
        const val MIN_READINGS = 6
        const val STABLE_MAD_MS = 8
        const val NCC_MIN = 0.30f
    }
}

// --- pure helpers (unit-tested) -----------------------------------------

/** A 12 ms Hann-windowed 1–6 kHz linear chirp. */
internal fun makeChirp(sampleRate: Int): FloatArray {
    val n = (sampleRate * 12 / 1000).coerceAtLeast(2)
    val f0 = 1_000.0
    val f1 = 6_000.0
    val dur = n.toDouble() / sampleRate
    return FloatArray(n) { i ->
        val t = i.toDouble() / sampleRate
        val phase = 2.0 * Math.PI * (f0 * t + (f1 - f0) / (2.0 * dur) * t * t)
        val w = 0.5 - 0.5 * cos(2.0 * Math.PI * i / (n - 1))
        (sin(phase) * w * 0.8).toFloat()
    }
}

/**
 * Best lag (samples) of [template] within `signal[from .. from+maxLag+m]`, by
 * normalised cross-correlation, and its NCC score. `(-1, 0f)` if there's no
 * room or the template is degenerate. Pure — unit-tested.
 */
internal fun bestLagSamples(
    template: FloatArray,
    signal: FloatArray,
    from: Int,
    maxLag: Int,
): Pair<Int, Float> {
    val m = template.size
    if (m < 2 || from < 0 || maxLag <= 0 || from + m > signal.size) return -1 to 0f
    var tNorm = 0.0
    for (v in template) tNorm += v.toDouble() * v
    tNorm = sqrt(tNorm)
    if (tNorm < 1e-9) return -1 to 0f

    var bestLag = -1
    var bestNcc = 0f
    var lag = 0
    while (lag <= maxLag && from + lag + m <= signal.size) {
        val off = from + lag
        var dot = 0.0
        var sNorm = 0.0
        for (i in 0 until m) {
            val s = signal[off + i].toDouble()
            dot += s * template[i]
            sNorm += s * s
        }
        sNorm = sqrt(sNorm)
        if (sNorm > 1e-9) {
            val ncc = (dot / (tNorm * sNorm)).toFloat()
            if (ncc > bestNcc) {
                bestNcc = ncc
                bestLag = lag
            }
        }
        lag++
    }
    return bestLag to bestNcc
}

internal fun median(values: List<Int>): Int {
    if (values.isEmpty()) return 0
    val s = values.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

internal fun medianAbsDev(values: List<Int>, med: Int): Int =
    if (values.isEmpty()) 0 else median(values.map { kotlin.math.abs(it - med) })
