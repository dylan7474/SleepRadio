package org.dylanjones.sleepradio.core.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs

/**
 * Plays the DJ's spoken links. Owns one short-lived [AudioTrack] per phrase
 * (`MODE_STATIC`), separate from Channel A / B / C — the OS sums the streams.
 *
 * Broadcast mode synthesises the *next* link while the current track plays
 * ([preload]) and fires it at the segue ([playPreloaded]) so there's no gap.
 * [speak] does both in one call for ad-hoc use (the debug test action).
 *
 * Plain class, no Hilt. Synthesis and playback run on a worker thread; every
 * public method returns immediately. Adapted from the DylanSpeaks
 * `PiperTtsManager` playback path.
 */
class DjVoicePlayer(private val engine: OfflineTtsEngine) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun postMain(delayMs: Long, block: () -> Unit) {
        mainHandler.postDelayed({ block() }, delayMs)
    }

    @Volatile private var track: AudioTrack? = null
    @Volatile private var pending: Clip? = null

    @Volatile
    var isSpeaking: Boolean = false
        private set

    /** A 16-bit mono clip ready to hand straight to an [AudioTrack]. */
    private class Clip(val pcm: ShortArray, val sampleRate: Int)

    /**
     * Synthesise [text] now and hold the result for the next [playPreloaded].
     * Blocks — call off the main thread. Returns false if synthesis failed.
     */
    fun preload(text: String, speed: Float = 1.0f): Boolean {
        val audio = engine.synth(text, speed) ?: return false
        pending = toClip(audio)
        return true
    }

    /**
     * Play whatever [preload] last produced. No-op (calls [onDone]) if nothing
     * is preloaded. [onDone] runs on the main thread after playback finishes.
     */
    fun playPreloaded(onDone: (() -> Unit)? = null) {
        val clip = pending
        pending = null
        if (clip == null) {
            onDone?.let { postMain(0, it) }
            return
        }
        isSpeaking = true
        Thread({
            try {
                render(clip)
            } catch (e: Throwable) {
                Log.e(TAG, "Playback failed", e)
            } finally {
                isSpeaking = false
                // Small tail gap so a listen-again loop doesn't catch our echo.
                onDone?.let { postMain(200, it) }
            }
        }, "dj-voice").start()
    }

    /** Synthesise and play [text] in one go (worker thread). */
    fun speak(text: String, speed: Float = 1.0f, onDone: (() -> Unit)? = null) {
        isSpeaking = true
        Thread({
            try {
                val audio = engine.synth(text, speed)
                if (audio != null) render(toClip(audio))
            } catch (e: Throwable) {
                Log.e(TAG, "speak() failed", e)
            } finally {
                isSpeaking = false
                onDone?.let { postMain(200, it) }
            }
        }, "dj-voice").start()
    }

    /** Stop any current playback and drop a preloaded clip. */
    fun stop() {
        pending = null
        val t = track
        track = null
        runCatching { t?.pause() }
        runCatching { t?.flush() }
        runCatching { t?.release() }
        isSpeaking = false
    }

    private fun toClip(audio: TtsAudio): Clip {
        val samples = audio.samples
        // Piper output peaks well below full scale (~0.5), lower on quiet
        // phrases — normalise toward 0 dBFS before the 16-bit conversion, with
        // a cap so near-silence isn't blown up.
        var peak = 0f
        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        val gain = if (peak > 0f) (0.97f / peak).coerceAtMost(6f) else 1f
        val pcm = ShortArray(samples.size) { i ->
            (samples[i] * gain * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return Clip(pcm, audio.sampleRate)
    }

    private fun render(clip: Clip) {
        stop() // release any previous track first
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(clip.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(clip.pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track = t
        t.write(clip.pcm, 0, clip.pcm.size, AudioTrack.WRITE_BLOCKING)
        t.play()

        // Wait out playback, then free the output — a lingering AudioTrack holds
        // the media path open.
        val playMs = clip.pcm.size * 1000L / clip.sampleRate + 150
        try {
            Thread.sleep(playMs)
        } catch (_: InterruptedException) {
            // stop() requested
        }
        if (track === t) {
            track = null
            runCatching { t.stop() }
            runCatching { t.release() }
        }
    }

    private companion object {
        const val TAG = "DjVoicePlayer"
    }
}
