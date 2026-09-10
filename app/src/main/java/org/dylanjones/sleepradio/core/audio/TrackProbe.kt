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
 * Just-in-time decode scan of a Broadcast Channel-A file, run a track ahead by
 * the sequencer (no playback, no `AudioTrack`). One pass yields both:
 *  - **Phase 12** — a loudness gain that levels the track against the rotation.
 *  - **Phase 14** — the leading / trailing silence bounds, so the `MediaItem`
 *    can be clipped to the real musical start/end and the DJ link follows the
 *    music tightly instead of after seconds of digital black.
 *
 * Results live only in a small in-session LRU cache — nothing is persisted.
 * Every failure path returns [TrackScan.NONE] (play the file untouched).
 */
class TrackProbe(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * @property gain    Phase 12 levelling gain (1f on any failure).
     * @property startMs Clip start — 0 unless real leading silence was found in range.
     * @property endMs   Clip end — 0 (= no clip, play to the natural end) unless
     *                   real trailing silence was found in range.
     */
    data class TrackScan(val gain: Float, val startMs: Long, val endMs: Long) {
        companion object {
            val NONE = TrackScan(1f, 0L, 0L)
        }
    }

    private val cache = object : LinkedHashMap<String, TrackScan>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackScan>?): Boolean =
            size > MAX_CACHE
    }

    /** Scan (and cache) [uri] on first ask; instant thereafter. */
    suspend fun scanFor(context: Context, uri: String): TrackScan = withContext(io) {
        synchronized(cache) { cache[uri] }?.let { return@withContext it }
        val scan = runCatching { analyse(context, Uri.parse(uri)) }
            .onFailure { Log.w(TAG, "track probe failed ($uri): ${it.message}") }
            .getOrDefault(TrackScan.NONE)
        synchronized(cache) { cache[uri] = scan }
        Log.d(
            TAG,
            "scan gain=${"%.2f".format(scan.gain)} clip=[${scan.startMs}..${scan.endMs}]ms for $uri",
        )
        scan
    }

    /** Prime the cache ahead of time; the result is discarded. */
    suspend fun warm(context: Context, uri: String) {
        scanFor(context, uri)
    }

    /** The already-computed scan for [uri], or null if it hasn't run yet. */
    fun cached(uri: String): TrackScan? = synchronized(cache) { cache[uri] }

    // --- decode -----------------------------------------------------------

    private fun analyse(context: Context, uri: Uri): TrackScan {
        val extractor = MediaExtractor()
        val durationMs: Long
        val trackIndex: Int
        try {
            extractor.setDataSource(context, uri, null)
            trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return TrackScan.NONE
            val format = extractor.getTrackFormat(trackIndex)
            durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION) / 1_000L
            } else {
                -1L
            }
        } finally {
            extractor.release()
        }

        val body = decodeWindows(context, uri, trackIndex, startMs = 0L, capMs = BODY_SCAN_MS)
            ?: return TrackScan.NONE
        if (!body.pcm16 || body.windowMeanSq.isEmpty()) return TrackScan.NONE

        val bounds = soundBounds(body.windowMeanSq, WINDOW_MS)
        val firstSoundMs = bounds[0]
        if (firstSoundMs < 0L) return TrackScan.NONE // nothing but silence

        val gain = rmsToGain(sqrt(meanOverSound(body.windowMeanSq)))

        // Trailing edge: from the body scan if it reached the end, else a short
        // seek-to-tail scan (bounded, whatever the track length).
        var lastSoundEndMs = if (body.reachedEos) bounds[1] else -1L
        if (lastSoundEndMs < 0L && durationMs > 0L) {
            val tailStartMs = (durationMs - TAIL_SCAN_MS).coerceAtLeast(0L)
            val tail = decodeWindows(
                context, uri, trackIndex,
                startMs = tailStartMs, capMs = TAIL_SCAN_MS + 4_000L,
            )
            if (tail != null && tail.pcm16) {
                val tb = soundBounds(tail.windowMeanSq, WINDOW_MS)
                if (tb[1] >= 0L) lastSoundEndMs = tailStartMs + tb[1]
            }
        }

        val fileEndMs = if (durationMs > 0L) durationMs else lastSoundEndMs
        val trim = edgeTrim(fileEndMs, firstSoundMs, lastSoundEndMs)
        return TrackScan(gain, trim[0], trim[1])
    }

    private class WindowScan(
        val pcm16: Boolean,
        val reachedEos: Boolean,
        /** Mean-square (0..1) of each ~[WINDOW_MS] window, in order. */
        val windowMeanSq: DoubleArray,
    )

    /**
     * Decode from [startMs] for up to [capMs] of audio, returning the per-window
     * mean-square envelope. Null on a decode setup failure.
     */
    private fun decodeWindows(
        context: Context,
        uri: Uri,
        trackIndex: Int,
        startMs: Long,
        capMs: Long,
    ): WindowScan? {
        val extractor = MediaExtractor()
        val codec: MediaCodec
        try {
            extractor.setDataSource(context, uri, null)
            extractor.selectTrack(trackIndex)
            if (startMs > 0L) extractor.seekTo(startMs * 1_000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
                extractor.release()
                return null
            }
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (e: Exception) {
            extractor.release()
            Log.w(TAG, "decodeWindows setup failed: ${e.message}")
            return null
        }

        val info = MediaCodec.BufferInfo()
        var sampleRate = 44_100
        var channels = 2
        var pcm16 = true
        var configured = false
        var samplesPerWindow = (sampleRate * channels * WINDOW_MS.toInt() / 1000).coerceAtLeast(1)
        var winSumSq = 0.0
        var winN = 0
        val windows = ArrayList<Double>(1_024)
        var totalSamples = 0L
        var inputDone = false
        var outputDone = false
        var reachedEos = false

        fun closeWindow() {
            if (winN > 0) windows.add(winSumSq / winN)
            winSumSq = 0.0
            winN = 0
        }

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)
                        val read = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (read < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
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
                        channels = out.getIntOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        pcm16 = out.getIntOr(KEY_PCM_ENCODING, ENCODING_PCM_16BIT) == ENCODING_PCM_16BIT
                        samplesPerWindow =
                            (sampleRate * channels * WINDOW_MS.toInt() / 1000).coerceAtLeast(1)
                        configured = true
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
                                    winSumSq += s * s
                                    winN++
                                    totalSamples++
                                    if (winN >= samplesPerWindow) closeWindow()
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                reachedEos = true
                                outputDone = true
                            }
                            if (configured &&
                                totalSamples / (sampleRate.toLong() * channels) * 1000L >= capMs
                            ) {
                                outputDone = true
                            }
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        closeWindow()
        return WindowScan(pcm16, reachedEos, windows.toDoubleArray())
    }

    private fun meanOverSound(windowMeanSq: DoubleArray): Double {
        val floorMs = SILENCE_FLOOR * SILENCE_FLOOR
        var sum = 0.0
        var n = 0
        for (ms in windowMeanSq) if (ms > floorMs) { sum += ms; n++ }
        return if (n > 0) sum / n else 0.0
    }

    private fun MediaFormat.getIntOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private companion object {
        const val TAG = "TrackProbe"
        const val TIMEOUT_US = 10_000L
        const val MAX_CACHE = 64

        /** Body scan cap — songs are minutes; short tracks reach EOS well inside this. */
        const val BODY_SCAN_MS = 120_000L

        /** For a track longer than the body cap: how much of the tail to seek-and-scan. */
        const val TAIL_SCAN_MS = 30_000L

        const val KEY_PCM_ENCODING = "pcm-encoding"
        const val ENCODING_PCM_16BIT = 2
    }
}

// --- pure helpers (unit-tested) -------------------------------------------

/** Envelope-window size for silence detection. */
internal const val WINDOW_MS = 20L

/** A window quieter than this (amplitude, ≈ −50 dBFS) counts as silence. */
internal const val SILENCE_FLOOR = 0.00316

/** Leading / trailing silence is only worth clipping inside these ranges (ms). */
internal const val LEAD_MIN_MS = 300L
internal const val LEAD_MAX_MS = 5_000L
internal const val LEAD_PAD_MS = 50L
internal const val TRIM_MIN_MS = 400L
internal const val TRIM_MAX_MS = 25_000L
internal const val TAIL_PAD_MS = 150L

/**
 * First-sound-start and last-sound-end, in ms, from a per-window mean-square
 * envelope. `[-1, -1]` when every window is below [SILENCE_FLOOR].
 */
internal fun soundBounds(windowMeanSq: DoubleArray, windowMs: Long): LongArray {
    val floorMs = SILENCE_FLOOR * SILENCE_FLOOR
    var first = -1
    var last = -1
    for (i in windowMeanSq.indices) {
        if (windowMeanSq[i] > floorMs) {
            if (first < 0) first = i
            last = i
        }
    }
    return if (first < 0) longArrayOf(-1L, -1L)
    else longArrayOf(first * windowMs, (last + 1) * windowMs)
}

/**
 * Turn detected sound bounds into a `[startMs, endMs]` clip. `0` means "don't
 * clip that edge": leading silence is only trimmed when it's in
 * [[LEAD_MIN_MS], [LEAD_MAX_MS]]; trailing black only in [[TRIM_MIN_MS],
 * [TRIM_MAX_MS]] before [fileEndMs]; a decay pad is left on. An inverted or
 * empty result collapses to no clip.
 */
internal fun edgeTrim(fileEndMs: Long, firstSoundMs: Long, lastSoundEndMs: Long): LongArray {
    var start = 0L
    if (firstSoundMs in LEAD_MIN_MS..LEAD_MAX_MS) {
        start = (firstSoundMs - LEAD_PAD_MS).coerceAtLeast(0L)
    }
    var end = 0L
    if (lastSoundEndMs in 1 until fileEndMs) {
        val trailing = fileEndMs - lastSoundEndMs
        if (trailing in TRIM_MIN_MS..TRIM_MAX_MS) {
            end = (lastSoundEndMs + TAIL_PAD_MS).coerceAtMost(fileEndMs)
        }
    }
    if (end != 0L && start >= end) return longArrayOf(0L, 0L)
    return longArrayOf(start, end)
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
