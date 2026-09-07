package org.dylanjones.sleepradio.core.tts

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * A resolved on-device Piper/VITS voice: the files sherpa-onnx needs to
 * synthesise speech. Voice packs live under `filesDir/tts/<id>/` and are put
 * there by the installer / importer (Phase 9 Chunk B) — nothing is bundled in
 * the APK.
 *
 * A directory counts as a valid pack when it holds `model.onnx`, `tokens.txt`
 * and an `espeak-ng-data/` directory.
 */
data class VoicePack(
    /** Directory name under `filesDir/tts/`, e.g. "stock" or "personal". */
    val id: String,
    /** Human label for the voice picker. */
    val displayName: String,
    val dir: File,
    val modelFile: File,
    val tokensFile: File,
    val dataDir: File,
    /** Model output rate, read from `model.onnx.json` if present (Piper = 22050). */
    val sampleRate: Int,
) {
    companion object {
        const val MODEL_NAME = "model.onnx"
        const val TOKENS_NAME = "tokens.txt"
        const val DATA_DIR_NAME = "espeak-ng-data"
        const val DEFAULT_SAMPLE_RATE = 22_050
    }
}

/**
 * Finds installed voice packs and picks which one to use.
 *
 * Resolution order for [preferred]: a personal / cloned voice wins over the
 * stock voice, and both win over anything else. A personal voice is only ever
 * present on the owner's own device — it is never in a build or a release.
 *
 * Plain class, no Hilt.
 */
class VoicePackResolver internal constructor(private val root: File) {

    constructor(context: Context) : this(File(context.filesDir, "tts"))

    /** Preferred id first, then stock, then the rest — only ids actually present. */
    private val order = listOf(ID_PERSONAL, ID_STOCK)

    fun installed(): List<VoicePack> {
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        val packs = dirs.mapNotNull { load(it) }
        return packs.sortedBy { p ->
            order.indexOf(p.id).let { if (it == -1) order.size else it }
        }
    }

    fun preferred(): VoicePack? = installed().firstOrNull()

    fun byId(id: String): VoicePack? = load(File(root, id))

    fun isInstalled(id: String): Boolean = load(File(root, id)) != null

    private fun load(dir: File): VoicePack? {
        if (!dir.isDirectory) return null
        val model = File(dir, VoicePack.MODEL_NAME)
        val tokens = File(dir, VoicePack.TOKENS_NAME)
        if (!model.isFile || model.length() == 0L) return null
        if (!tokens.isFile || tokens.length() == 0L) return null
        // A pack may ship its own espeak-ng-data, or (for a model-only personal
        // import) borrow the stock pack's copy — both are en-us espeak phonemes.
        val ownData = File(dir, VoicePack.DATA_DIR_NAME)
        val data = when {
            ownData.isDirectory -> ownData
            else -> File(File(root, ID_STOCK), VoicePack.DATA_DIR_NAME).takeIf { it.isDirectory }
        } ?: return null
        return VoicePack(
            id = dir.name,
            displayName = displayNameFor(dir.name),
            dir = dir,
            modelFile = model,
            tokensFile = tokens,
            dataDir = data,
            sampleRate = readSampleRate(File(dir, "${VoicePack.MODEL_NAME}.json")),
        )
    }

    private fun displayNameFor(id: String): String = when (id) {
        ID_STOCK -> "Stock voice"
        ID_PERSONAL -> "My voice"
        else -> id.replaceFirstChar { it.uppercase() }
    }

    private fun readSampleRate(json: File): Int {
        if (!json.isFile) return VoicePack.DEFAULT_SAMPLE_RATE
        return try {
            JSONObject(json.readText())
                .optJSONObject("audio")
                ?.optInt("sample_rate", VoicePack.DEFAULT_SAMPLE_RATE)
                ?: VoicePack.DEFAULT_SAMPLE_RATE
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read sample_rate from ${json.name}: ${e.message}")
            VoicePack.DEFAULT_SAMPLE_RATE
        }
    }

    companion object {
        private const val TAG = "VoicePackResolver"
        const val ID_STOCK = "stock"
        const val ID_PERSONAL = "personal"
    }
}
