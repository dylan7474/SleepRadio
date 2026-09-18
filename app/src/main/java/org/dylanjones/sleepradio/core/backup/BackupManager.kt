package org.dylanjones.sleepradio.core.backup

import android.content.Context
import kotlinx.coroutines.flow.first
import org.dylanjones.sleepradio.core.data.SettingsRepository
import org.dylanjones.sleepradio.core.data.db.AudiobookProgressDao
import org.dylanjones.sleepradio.core.data.db.AudiobookProgressEntity
import org.dylanjones.sleepradio.core.data.db.PodcastFeedDao
import org.dylanjones.sleepradio.core.data.db.PodcastFeedEntity
import org.dylanjones.sleepradio.core.data.db.PodcastProgressDao
import org.dylanjones.sleepradio.core.data.db.PodcastProgressEntity
import org.dylanjones.sleepradio.core.data.db.SourceSlotDao
import org.dylanjones.sleepradio.core.data.db.SourceSlotEntity
import org.dylanjones.sleepradio.core.tts.VoicePackResolver
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Bundles everything a reinstall (or a debug/release signing-key swap) otherwise
 * loses: DataStore settings, the Room tables (source slots, audiobook/podcast
 * progress, podcast subscriptions), and any installed voice packs
 * (`filesDir/tts/<id>/`) — into one `.zip` the user picks where to keep, and
 * back again.
 *
 * SAF folder GRANTS can't be backed up — Android revokes those at the OS level
 * on uninstall, regardless of what's in the zip — so the three tree-URI
 * settings are deliberately excluded from the restored preferences (see
 * [EXCLUDED_SETTINGS_KEYS]); [RestoreResult.foldersToRepick] just tells the
 * caller which folders were assigned, so the UI can prompt a re-pick instead of
 * silently pointing at a URI the app can no longer read.
 *
 * Plain class, no Hilt (see
 * [org.dylanjones.sleepradio.core.tts.VoicePackInstaller] for why: a fresh
 * @Inject type referenced from a @HiltViewModel trips KSP2 on this toolchain).
 * Suspend methods do blocking IO; call them on [kotlinx.coroutines.Dispatchers.IO].
 */
class BackupManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val sourceSlotDao: SourceSlotDao,
    private val audiobookProgressDao: AudiobookProgressDao,
    private val podcastFeedDao: PodcastFeedDao,
    private val podcastProgressDao: PodcastProgressDao,
) {
    data class RestoreResult(
        val voicesRestored: List<String>,
        val foldersToRepick: List<String>,
    )

    suspend fun export(output: OutputStream) {
        val manifest = buildManifest()
        val settingsJson = encodeSettings(settings.exportAll())
        val dbJson = exportDb()
        ZipOutputStream(output.buffered()).use { zip ->
            writeEntry(zip, "manifest.json", manifest.toString(2))
            writeEntry(zip, "settings.json", settingsJson.toString())
            writeEntry(zip, "db.json", dbJson.toString())
            for (id in listOf(VoicePackResolver.ID_PERSONAL, VoicePackResolver.ID_STOCK)) {
                val dir = File(File(context.filesDir, "tts"), id)
                if (dir.isDirectory) addDirToZip(zip, dir, "voices/$id")
            }
        }
    }

    suspend fun import(input: InputStream): RestoreResult {
        val staging = File(context.cacheDir, ".backup-restore").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            unzip(input, staging)

            File(staging, "settings.json").takeIf { it.isFile }?.let {
                val values = decodeSettings(JSONObject(it.readText()))
                settings.importAll(values - EXCLUDED_SETTINGS_KEYS)
            }
            File(staging, "db.json").takeIf { it.isFile }?.let {
                importDb(JSONObject(it.readText()))
            }

            val restoredVoices = mutableListOf<String>()
            File(staging, "voices").listFiles { f -> f.isDirectory }?.forEach { src ->
                val dest = File(File(context.filesDir, "tts"), src.name)
                dest.deleteRecursively()
                src.copyRecursively(dest, overwrite = true)
                restoredVoices += src.name
            }

            val manifest = File(staging, "manifest.json").takeIf { it.isFile }
                ?.let { JSONObject(it.readText()) }
            val folders = manifest?.optJSONObject("folders")
            val foldersToRepick = folders?.keys()?.asSequence()
                ?.filter { folders.optString(it).isNotBlank() }
                ?.toList().orEmpty()

            return RestoreResult(restoredVoices, foldersToRepick)
        } finally {
            staging.deleteRecursively()
        }
    }

    // --- manifest ---

    private suspend fun buildManifest(): JSONObject {
        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val folders = JSONObject().apply {
            put("Music", settings.musicTreeUri.first() ?: "")
            put("Audiobooks", settings.audiobooksTreeUri.first() ?: "")
            put("Jingles", settings.jinglesTreeUri.first() ?: "")
        }
        return JSONObject().apply {
            put("createdAt", System.currentTimeMillis())
            put("versionName", pkgInfo?.versionName ?: "")
            put("versionCode", pkgInfo?.longVersionCode ?: 0L)
            put("folders", folders)
        }
    }

    // --- Room <-> JSON ---

    private suspend fun exportDb(): JSONObject = JSONObject().apply {
        put("slots", JSONArray(sourceSlotDao.observeAll().first().map(::slotJson)))
        put("audiobookProgress", JSONArray(audiobookProgressDao.getAll().map(::progressJson)))
        put("podcastFeeds", JSONArray(podcastFeedDao.observeAll().first().map(::feedJson)))
        put("podcastProgress", JSONArray(podcastProgressDao.getAll().map(::podProgressJson)))
    }

    private suspend fun importDb(obj: JSONObject) {
        obj.optJSONArray("slots")?.forEachObject { sourceSlotDao.upsert(slotFromJson(it)) }
        obj.optJSONArray("audiobookProgress")?.forEachObject { audiobookProgressDao.upsert(progressFromJson(it)) }
        obj.optJSONArray("podcastFeeds")?.forEachObject { podcastFeedDao.upsert(feedFromJson(it)) }
        obj.optJSONArray("podcastProgress")?.forEachObject { podcastProgressDao.upsert(podProgressFromJson(it)) }
    }

    private fun slotJson(e: SourceSlotEntity) = JSONObject().apply {
        put("slotIndex", e.slotIndex)
        put("type", e.type)
        put("refId", e.refId)
        put("label", e.label)
        put("sublabel", e.sublabel)
        put("artworkUri", e.artworkUri ?: JSONObject.NULL)
    }

    private fun slotFromJson(o: JSONObject) = SourceSlotEntity(
        slotIndex = o.getInt("slotIndex"),
        type = o.getString("type"),
        refId = o.getString("refId"),
        label = o.getString("label"),
        sublabel = o.getString("sublabel"),
        artworkUri = o.optString("artworkUri").takeIf { it.isNotBlank() && !o.isNull("artworkUri") },
    )

    private fun progressJson(e: AudiobookProgressEntity) = JSONObject().apply {
        put("bookId", e.bookId)
        put("chapterIndex", e.chapterIndex)
        put("positionMs", e.positionMs)
        put("updatedAt", e.updatedAt)
    }

    private fun progressFromJson(o: JSONObject) = AudiobookProgressEntity(
        bookId = o.getString("bookId"),
        chapterIndex = o.getInt("chapterIndex"),
        positionMs = o.getLong("positionMs"),
        updatedAt = o.getLong("updatedAt"),
    )

    private fun feedJson(e: PodcastFeedEntity) = JSONObject().apply {
        put("id", e.id)
        put("feedUrl", e.feedUrl)
        put("title", e.title)
        put("artworkUrl", e.artworkUrl ?: JSONObject.NULL)
        put("addedAt", e.addedAt)
    }

    private fun feedFromJson(o: JSONObject) = PodcastFeedEntity(
        id = o.getString("id"),
        feedUrl = o.getString("feedUrl"),
        title = o.getString("title"),
        artworkUrl = o.optString("artworkUrl").takeIf { it.isNotBlank() && !o.isNull("artworkUrl") },
        addedAt = o.getLong("addedAt"),
    )

    private fun podProgressJson(e: PodcastProgressEntity) = JSONObject().apply {
        put("episodeGuid", e.episodeGuid)
        put("feedId", e.feedId)
        put("positionMs", e.positionMs)
        put("durationMs", e.durationMs)
        put("completed", e.completed)
        put("updatedAt", e.updatedAt)
    }

    private fun podProgressFromJson(o: JSONObject) = PodcastProgressEntity(
        episodeGuid = o.getString("episodeGuid"),
        feedId = o.getString("feedId"),
        positionMs = o.getLong("positionMs"),
        durationMs = o.getLong("durationMs"),
        completed = o.getBoolean("completed"),
        updatedAt = o.getLong("updatedAt"),
    )

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) block(getJSONObject(i))
    }

    // --- zip helpers (mirrors VoicePackInstaller's traversal-guarded unzip) ---

    private fun writeEntry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray())
        zip.closeEntry()
    }

    private fun addDirToZip(zip: ZipOutputStream, dir: File, prefix: String) {
        dir.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = prefix + "/" + f.relativeTo(dir).path.replace(File.separatorChar, '/')
            zip.putNextEntry(ZipEntry(rel))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun unzip(input: InputStream, dest: File) {
        val destPath = dest.canonicalPath
        ZipInputStream(input.buffered()).use { zin ->
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

    companion object {
        /**
         * DataStore keys never restored verbatim: a SAF tree-URI grant doesn't
         * survive a reinstall, so blindly restoring the string would leave a
         * "folder is set" UI pointing at a URI the app has no permission to
         * read. [buildManifest] records these separately, purely informational.
         */
        private val EXCLUDED_SETTINGS_KEYS =
            setOf("music_tree_uri", "audiobooks_tree_uri", "jingles_tree_uri")
    }
}

// --- settings <-> JSON (file-level, pure, unit-testable) ---

internal fun encodeSettings(values: Map<String, Any>): JSONObject {
    val obj = JSONObject()
    for ((k, v) in values) {
        obj.put(k, if (v is Set<*>) JSONArray(v.toList()) else v)
    }
    return obj
}

internal fun decodeSettings(obj: JSONObject): Map<String, Any> {
    val out = LinkedHashMap<String, Any>()
    obj.keys().forEach { k ->
        when (val v = obj.get(k)) {
            is JSONArray -> out[k] = (0 until v.length()).mapTo(LinkedHashSet()) { v.getString(it) }
            is Boolean, is Int, is Long, is Double, is String -> out[k] = v
            // A decimal literal parses as Double on Android's bundled org.json
            // (what actually runs on-device) but as BigDecimal on the newer
            // org.json:json artifact (only ever on the JVM unit-test
            // classpath) -- normalise both to Double so importAll() only
            // ever has to handle one decimal type.
            is java.math.BigDecimal -> out[k] = v.toDouble()
            else -> Unit
        }
    }
    return out
}
