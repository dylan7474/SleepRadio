package org.dylanjones.sleepradio.core.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelConfig
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper over ML Kit's on-device Gemini Nano Prompt API (AICore), built
 * for Phase 18 DJ commentary but domain-agnostic itself — it just runs a
 * prompt and hands back text, or nothing. Plain class, no Hilt: a fresh
 * `@Inject` type referenced from a `@HiltViewModel` trips KSP2 on this
 * toolchain (same reason [org.dylanjones.sleepradio.core.tts.VoicePackInstaller]
 * and [org.dylanjones.sleepradio.core.audio.TrackProbe] are plain classes).
 *
 * Every call is best-effort: a failure anywhere (model unavailable, a
 * malformed/empty response, an exception from the AICore binder) surfaces as
 * `null`/[FeatureStatus.UNAVAILABLE], never as a thrown exception — the
 * caller's job is always "use this if it's there, fall back to the template
 * if not", not to handle AICore-specific errors.
 */
class DjCommentaryEngine {

    /** FAST over FULL: this runs on the playback-gap critical path (see
     *  [org.dylanjones.sleepradio.playback.PlaybackConnection]'s pre-synth
     *  budget), where a quicker, slightly less elaborate reply beats a richer
     *  one that risks missing the segue and falling back anyway. */
    private val model: GenerativeModel by lazy {
        Generation.getClient(
            generationConfig {
                modelConfig = ModelConfig.builder().apply { preference = ModelPreference.FAST }.build()
            },
        )
    }

    /** One of [FeatureStatus]'s `UNAVAILABLE`/`DOWNLOADABLE`/`DOWNLOADING`/`AVAILABLE`. */
    suspend fun status(): Int =
        runCatching { model.checkStatus() }
            .onFailure { Log.w(TAG, "checkStatus failed", it) }
            .getOrDefault(FeatureStatus.UNAVAILABLE)

    /** Triggers (or observes, if already running) the AICore feature download. */
    fun download(): Flow<DownloadStatus> = model.download()

    /** Best-effort warm-up so the first real [generateLink] isn't also a cold model load. */
    suspend fun warmup() {
        runCatching { model.warmup() }.onFailure { Log.w(TAG, "warmup failed", it) }
    }

    /**
     * Ask the on-device model for one short line. Returns null on any
     * failure, timeout upstream (the caller wraps this in its own
     * `withTimeoutOrNull`), or empty output — never throws.
     */
    suspend fun generateLink(systemInstruction: String, prompt: String): String? =
        runCatching {
            val request = generateContentRequest(SystemInstruction(systemInstruction), TextPart(prompt)) {
                temperature = 0.9f
                candidateCount = 1
                maxOutputTokens = 80
            }
            model.generateContent(request).candidates.firstOrNull()?.text
        }.onFailure { Log.w(TAG, "generateLink failed", it) }.getOrNull()?.takeIf { it.isNotBlank() }

    fun close() {
        runCatching { model.close() }
    }

    private companion object {
        const val TAG = "DjCommentaryEngine"
    }
}
