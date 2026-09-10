package org.dylanjones.sleepradio.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Just-in-time loudness measurement for Broadcast Channel-A audio (Phase 12).
 * Given the URI of a track or jingle about to play, decode-scans it once
 * (no playback, no `AudioTrack`) and returns a linear gain that brings it to a
 * common target loudness — < 1 for a hot master, > 1 for a quiet one.
 *
 * Nothing is written to storage: results live only in a small in-session LRU
 * cache. Callers scan the *next* item during the current one (the broadcast
 * sequencer already runs a track ahead), so the decode cost is hidden. Every
 * failure path returns 1f — play it untouched, never throw.
 */
class LoudnessProbe(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val cache = object : LinkedHashMap<String, Float>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean =
            size > MAX_CACHE
    }

    /** Gain for [uri], measured (and cached) on first ask, instant thereafter. */
    suspend fun gainFor(context: Context, uri: String): Float = withContext(io) {
        synchronized(cache) { cache[uri] }?.let { return@withContext it }
        val gain = runCatching { analyse(context, Uri.parse(uri)) }
            .onFailure { Log.w(TAG, "loudness probe failed ($uri): ${it.message}") }
            .getOrDefault(1f)
        synchronized(cache) { cache[uri] = gain }
        Log.d(TAG, "loudness gain ${"%.2f".format(gain)} for $uri")
        gain
    }

    /** Prime the cache ahead of time; the result is discarded. */
    suspend fun warm(context: Context, uri: String) {
        gainFor(context, uri)
    }

    /** The already-measured gain for [uri], or null if it hasn't been scanned yet. */
    fun cached(uri: String): Float? = synchronized(cache) { cache[uri] }

    private fun analyse(context: Context, uri: Uri): Float {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return 1f
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return 1f
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            try {
                return measure(extractor, codec)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun measure(extractor: MediaExtractor, codec: MediaCodec): Float {
        val info = MediaCodec.BufferInfo()
        var sumSquares = 0.0
        var sampleCount = 0L
        var sampleRate = 44_100
        var pcm16 = true
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)
                    val read = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                    if (read < 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val out = codec.outputFormat
                    sampleRate = out.getIntOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                    pcm16 = out.getIntOr(KEY_PCM_ENCODING, ENCODING_PCM_16BIT) == ENCODING_PCM_16BIT
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> {
                    if (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (pcm16 && outBuf != null && info.size > 1) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val shorts = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            while (shorts.hasRemaining()) {
                                val s = shorts.get().toDouble() / 32_768.0
                                sumSquares += s * s
                                sampleCount++
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (sampleCount >= SCAN_SECONDS.toLong() * sampleRate * 2) outputDone = true
                    }
                }
            }
        }

        if (!pcm16 || sampleCount < MIN_SAMPLES) return 1f
        return rmsToGain(sqrt(sumSquares / sampleCount))
    }

    private fun MediaFormat.getIntOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private companion object {
        const val TAG = "LoudnessProbe"
        const val TIMEOUT_US = 10_000L
        const val MAX_CACHE = 64
        const val MIN_SAMPLES = 4_096L

        /** Stop scanning after this much audio — songs are minutes; guards a pathological file. */
        const val SCAN_SECONDS = 120

        // MediaFormat.KEY_PCM_ENCODING / AudioFormat.ENCODING_PCM_16BIT without the API-24 imports.
        const val KEY_PCM_ENCODING = "pcm-encoding"
        const val ENCODING_PCM_16BIT = 2
    }
}

/**
 * Target RMS as a fraction of 16-bit full scale (~ −19 dBFS). Comfortably under
 * [org.dylanjones.sleepradio.core.tts.DjVoicePlayer]'s 0.30 speech target, so the
 * DJ still sits ~9 dB over the music bed. One-line loudness tune.
 */
internal const val LOUDNESS_TARGET_RMS = 0.11

/** RMS (0..1) → levelling gain, clamped to [MixerController]'s bounds. Pure — unit-tested. */
internal fun rmsToGain(rms: Double): Float {
    if (rms < 1e-4) return 1f // effectively silent — nothing worth boosting
    return (LOUDNESS_TARGET_RMS / rms).toFloat()
        .coerceIn(MixerController.MIN_ITEM_GAIN, MixerController.MAX_ITEM_GAIN)
}
