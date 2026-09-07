package org.dylanjones.sleepradio.core.tts

import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** One synthesised phrase: mono float PCM in [-1, 1] plus its rate. */
class TtsAudio(val samples: FloatArray, val sampleRate: Int)

/**
 * Thin wrapper around sherpa-onnx [OfflineTts] for a single loaded [VoicePack].
 *
 * Deliberately a plain class (no Hilt — keeps clear of the KSP2 new-injectable
 * problem the ambient generators hit) and deliberately synchronous: [load] and
 * [synth] block the calling thread and must be called off the main thread. The
 * VITS engine init is a few hundred ms; a short line synthesises well under a
 * second on the Pixel 9.
 *
 * All methods are safe to call from multiple threads; work is serialised on an
 * internal lock.
 */
class OfflineTtsEngine {

    private val lock = ReentrantLock()
    private var tts: OfflineTts? = null
    private var loadedId: String? = null

    val loadedPackId: String? get() = lock.withLock { loadedId }
    val isReady: Boolean get() = lock.withLock { tts != null }

    /** Load [pack] if a different pack (or nothing) is loaded. Returns success. */
    fun ensureLoaded(pack: VoicePack): Boolean = lock.withLock {
        if (tts != null && loadedId == pack.id) return true
        releaseLocked()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = pack.modelFile.absolutePath,
                    lexicon = "",
                    tokens = pack.tokensFile.absolutePath,
                    dataDir = pack.dataDir.absolutePath,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f,
                ),
                numThreads = 1,
                debug = false,
            ),
        )
        return try {
            tts = OfflineTts(config = config)
            loadedId = pack.id
            Log.d(TAG, "Loaded voice '${pack.id}' (${pack.modelFile.length()} bytes)")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Voice engine init failed for '${pack.id}'", e)
            tts = null
            loadedId = null
            false
        }
    }

    /**
     * Synthesise [text] at [speed] (1.0 = the model's natural rate, higher is
     * faster). Returns null if no voice is loaded or synthesis fails.
     */
    fun synth(text: String, speed: Float = 1.0f): TtsAudio? = lock.withLock {
        val engine = tts ?: return null
        if (text.isBlank()) return null
        return try {
            val t0 = System.currentTimeMillis()
            val out = engine.generate(text = text, sid = 0, speed = speed)
            Log.d(TAG, "synth ${out.samples.size} samples @ ${out.sampleRate}Hz " +
                "in ${System.currentTimeMillis() - t0}ms: \"$text\"")
            TtsAudio(out.samples, out.sampleRate)
        } catch (e: Throwable) {
            Log.e(TAG, "Synthesis failed", e)
            null
        }
    }

    fun release() = lock.withLock { releaseLocked() }

    private fun releaseLocked() {
        runCatching { tts?.release() }
        tts = null
        loadedId = null
    }

    private companion object {
        const val TAG = "OfflineTtsEngine"
    }
}
