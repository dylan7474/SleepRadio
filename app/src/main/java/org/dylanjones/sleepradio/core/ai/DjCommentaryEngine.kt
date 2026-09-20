package org.dylanjones.sleepradio.core.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.delay
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
 *
 * `Generation.getClient()` with no [com.google.mlkit.genai.prompt.GenerationConfig]
 * — deliberately matching the exact call shape proven working in the sibling
 * `DylanSpeaks` project's `AiReplyManager` on this same hardware, rather than
 * introducing an untested [com.google.mlkit.genai.prompt.ModelConfig]/
 * [com.google.mlkit.genai.prompt.ModelPreference] customisation. A latency
 * tune can be reintroduced later, proven working first.
 */
class DjCommentaryEngine {

    private val model: GenerativeModel by lazy { Generation.getClient() }

    /**
     * One of [FeatureStatus]'s `UNAVAILABLE`/`DOWNLOADABLE`/`DOWNLOADING`/`AVAILABLE`.
     * `UNAVAILABLE` on the very first call can just mean AICore hasn't finished
     * fetching its config yet for this app — `DylanSpeaks` hit the same thing
     * and retries a few times before concluding the device really can't do it;
     * mirrored here rather than trusting a single cold-start check.
     */
    suspend fun status(): Int {
        repeat(STATUS_RETRIES + 1) { attempt ->
            val result = runCatching { model.checkStatus() }
                .onFailure { Log.w(TAG, "checkStatus failed (attempt $attempt)", it) }
                .getOrDefault(FeatureStatus.UNAVAILABLE)
            Log.d(TAG, "checkStatus (attempt $attempt) = ${statusName(result)}")
            if (result != FeatureStatus.UNAVAILABLE || attempt == STATUS_RETRIES) return result
            delay(STATUS_RETRY_DELAY_MS)
        }
        return FeatureStatus.UNAVAILABLE
    }

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
    suspend fun generateLink(
        systemInstruction: String,
        prompt: String,
        temperature: Float = 0.9f,
        topK: Int? = null,
    ): String? =
        runCatching {
            val request = generateContentRequest(SystemInstruction(systemInstruction), TextPart(prompt)) {
                this.temperature = temperature
                topK?.let { this.topK = it }
                candidateCount = 1
                maxOutputTokens = 80
            }
            model.generateContent(request).candidates.firstOrNull()?.text
        }.onFailure { Log.w(TAG, "generateLink failed", it) }.getOrNull()?.takeIf { it.isNotBlank() }

    fun close() {
        runCatching { model.close() }
    }

    private fun statusName(s: Int): String = when (s) {
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        FeatureStatus.DOWNLOADING -> "DOWNLOADING"
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        else -> "unknown($s)"
    }

    private companion object {
        const val TAG = "DjCommentaryEngine"
        const val STATUS_RETRIES = 3
        const val STATUS_RETRY_DELAY_MS = 3_000L
    }
}
