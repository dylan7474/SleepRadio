package org.dylanjones.sleepradio.core.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Installs voice packs into `filesDir/tts/<id>/`. Nothing is bundled in the APK
 * (see NOTICE) — a pack arrives either as a `.zip` the user picks, or (for the
 * stock voice) a plain static download. No API key, no account.
 *
 * A valid `.zip` contains, at its root or one directory down:
 *   - a `*.onnx` model  (stored as `model.onnx`)
 *   - `tokens.txt`
 *   - optionally `*.onnx.json` (stored as `model.onnx.json` — for the sample rate)
 *   - optionally `espeak-ng-data/` (a model-only personal import borrows the
 *     stock pack's copy, resolved by [VoicePackResolver])
 *
 * Plain class, no Hilt (a fresh @Inject type referenced from a @HiltViewModel
 * trips KSP2 on this toolchain — see the ambient generators). Suspend methods
 * do blocking IO; call them on [kotlinx.coroutines.Dispatchers.IO].
 */
class VoicePackInstaller internal constructor(private val root: File) {

    constructor(context: Context) : this(File(context.filesDir, "tts"))

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    sealed interface InstallState {
        data object Idle : InstallState
        /** [pct] is 0..100, or -1 when indeterminate. */
        data class Working(val pct: Int) : InstallState
        data object Ready : InstallState
        data class Failed(val reason: String) : InstallState
    }

    fun resetState() { _state.value = InstallState.Idle }

    /** Download + install the stock voice from [STOCK_URL]. */
    suspend fun downloadStock(): Boolean {
        _state.value = InstallState.Working(0)
        val tmp = File(root, ".incoming.zip")
        return try {
            root.mkdirs()
            val sha = downloadTo(URL(STOCK_URL), tmp) { pct -> _state.value = InstallState.Working(pct / 2) }
            if (STOCK_SHA256.isNotBlank() && !sha.equals(STOCK_SHA256, ignoreCase = true)) {
                fail("Checksum mismatch — download corrupt")
            } else {
                extractAndSwap(tmp, VoicePackResolver.ID_STOCK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadStock failed", e)
            fail(e.message ?: e.javaClass.simpleName)
        } finally {
            tmp.delete()
        }
    }

    /**
     * Install a `.zip` opened by [open] as pack [id] (typically "stock" or
     * "personal"). [open] may be called more than once (checksum, then extract).
     */
    suspend fun installZip(id: String, open: () -> InputStream): Boolean {
        _state.value = InstallState.Working(-1)
        val tmp = File(root, ".incoming-$id.zip")
        return try {
            root.mkdirs()
            open().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            extractAndSwap(tmp, id)
        } catch (e: Exception) {
            Log.e(TAG, "installZip($id) failed", e)
            fail(e.message ?: e.javaClass.simpleName)
        } finally {
            tmp.delete()
        }
    }

    fun remove(id: String) {
        File(root, id).deleteRecursively()
        if (_state.value is InstallState.Ready) _state.value = InstallState.Idle
    }

    // --- internals ---

    private fun fail(reason: String): Boolean {
        _state.value = InstallState.Failed(reason)
        return false
    }

    private fun extractAndSwap(zip: File, id: String): Boolean {
        val staging = File(root, ".staging-$id").apply { deleteRecursively(); mkdirs() }
        try {
            unzip(zip, staging)
            val src = locatePackRoot(staging) ?: return fail("Zip has no model.onnx")
            normalise(src) ?: return fail("Zip missing tokens.txt")

            // Make the pack self-contained. A model-only import (typical for a
            // personal voice) has no espeak-ng-data of its own — copy the stock
            // pack's (both are en-* espeak phonemes; it's ~1.8 MB trimmed). If
            // there's no stock either, VoicePackResolver still borrows at load
            // time, but a self-contained copy survives the stock pack being
            // removed.
            val ownData = File(src, VoicePack.DATA_DIR_NAME)
            if (!ownData.isDirectory) {
                val stockData = File(File(root, "stock"), VoicePack.DATA_DIR_NAME)
                if (stockData.isDirectory) {
                    stockData.copyRecursively(ownData, overwrite = true)
                    Log.d(TAG, "Copied stock espeak-ng-data into '$id'")
                }
            }

            val dest = File(root, id)
            dest.deleteRecursively()
            if (!src.renameTo(dest)) {
                src.copyRecursively(dest, overwrite = true)
                src.deleteRecursively()
            }
            _state.value = InstallState.Ready
            Log.d(TAG, "Installed voice '$id' -> ${dest.absolutePath}")
            return true
        } finally {
            staging.deleteRecursively()
        }
    }

    /** The dir directly holding a `*.onnx` — root, or the sole subdirectory. */
    private fun locatePackRoot(dir: File): File? {
        if (dir.listFiles()?.any { it.isFile && it.name.endsWith(".onnx") } == true) return dir
        val subs = dir.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (subs.size == 1) return locatePackRoot(subs[0])
        return null
    }

    /**
     * Rename the model / config / tokens to the canonical names in-place.
     * Returns null if `tokens.txt` is absent.
     */
    private fun normalise(dir: File): Unit? {
        val files = dir.listFiles().orEmpty()
        files.firstOrNull { it.name.endsWith(".onnx.json") }
            ?.takeIf { it.name != "${VoicePack.MODEL_NAME}.json" }
            ?.renameTo(File(dir, "${VoicePack.MODEL_NAME}.json"))
        files.firstOrNull { it.name.endsWith(".onnx") && !it.name.endsWith(".onnx.json") }
            ?.takeIf { it.name != VoicePack.MODEL_NAME }
            ?.renameTo(File(dir, VoicePack.MODEL_NAME))
        if (!File(dir, VoicePack.MODEL_NAME).isFile) return null
        if (!File(dir, VoicePack.TOKENS_NAME).isFile) return null
        return Unit
    }

    private fun unzip(zip: File, dest: File) {
        val destPath = dest.canonicalPath
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                if (!out.canonicalPath.startsWith(destPath + File.separator)) {
                    throw SecurityException("Zip entry escapes target: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
    }

    /** Streams [url] to [file], returns the lowercase hex SHA-256. */
    private fun downloadTo(url: URL, file: File, onProgress: (Int) -> Unit): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                file.outputStream().use { fout ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } >= 0) {
                        fout.write(buf, 0, read)
                        digest.update(buf, 0, read)
                        done += read
                        if (total > 0) onProgress(((done * 100) / total).toInt())
                    }
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "VoicePackInstaller"

        /**
         * Static release asset — a plain GET, no key. Published separately from
         * the app (keeps the GPL-relevant voice/eSpeak data out of the repo and
         * the APK). Until the asset exists the download fails cleanly and the
         * file-picker import path still works.
         */
        const val STOCK_URL =
            "https://github.com/dylan7474/SleepRadio/releases/download/voice-stock-v1/" +
                "sleepradio-voice-en_GB-v1.zip"

        /**
         * Lowercase hex SHA-256 of the asset at [STOCK_URL]; blank = skip check.
         * Matches the `.zip` produced by `tools/build-stock-voice.sh`
         * (en_GB-southern_english_female-low + English-only espeak-ng-data).
         */
        const val STOCK_SHA256 = "8ed5f60ca1266e8f3f7d898981704bd736366175a5c35998eb0af20f0570b70e"
    }
}
