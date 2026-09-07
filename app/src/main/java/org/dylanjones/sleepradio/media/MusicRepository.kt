package org.dylanjones.sleepradio.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import org.dylanjones.sleepradio.core.broadcast.BroadcastTrack
import org.dylanjones.sleepradio.core.data.Chapter
import org.dylanjones.sleepradio.core.data.FolderAlbum
import org.dylanjones.sleepradio.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads tracks from the on-device [MediaStore] (legacy `SourceType.ALBUM` slots
 * only) and walks user-chosen SAF folder trees for the folder-based music
 * library. Requires `READ_MEDIA_AUDIO` for the MediaStore path.
 */
@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val albumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")

    /** Tracks for a legacy MediaStore album id (from slots assigned before the
     * music source became folder-only). */
    suspend fun tracksForAlbum(albumId: Long): List<Track> = withContext(io) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
        )
        val selection =
            "${MediaStore.Audio.Media.ALBUM_ID} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val out = ArrayList<Track>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            arrayOf(albumId.toString()),
            "${MediaStore.Audio.Media.TRACK} ASC, ${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val artwork = ContentUris.withAppendedId(albumArtBase, albumId)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out += Track(
                    id = id,
                    title = c.getString(titleCol) ?: "Unknown track",
                    artist = c.getString(artistCol) ?: "Unknown artist",
                    albumTitle = c.getString(albumCol) ?: "",
                    durationMs = c.getLong(durCol),
                    trackNumber = c.getInt(trackCol) % 1000,
                    contentUri = ContentUris.withAppendedId(collection, id),
                    artworkUri = artwork,
                )
            }
        }
        out
    }

    // --- SAF music folder (an alternative to the MediaStore library) ---

    private val audioExtensions =
        setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "mka", "wma")

    /** Any folder under [treeUri] that directly contains audio files is an "album". */
    suspend fun folderAlbums(treeUri: String): List<FolderAlbum> = withContext(io) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        val out = ArrayList<FolderAlbum>()
        if (root != null) {
            val rootName = root.name ?: ""
            val pending = ArrayDeque<Triple<DocumentFile, Int, String>>()
            pending.add(Triple(root, 0, ""))
            while (pending.isNotEmpty()) {
                val (dir, depth, parentName) = pending.removeFirst()
                val children = dir.listFiles()
                val count = children.count { it.isFile && isAudioFile(it) }
                if (count > 0) {
                    val artist = if (parentName.isNotBlank() && parentName != rootName) parentName else ""
                    out += FolderAlbum(dir.uri.toString(), dir.name ?: "Music", artist, count)
                }
                if (depth < 3) {
                    for (child in children) {
                        if (child.isDirectory) pending.add(Triple(child, depth + 1, dir.name ?: parentName))
                    }
                }
            }
        }
        out.sortedBy { it.artist.lowercase() + " " + it.title.lowercase() }
    }

    /** Sorted audio files directly inside the folder-album [albumId] under [treeUri]. */
    suspend fun folderTracks(treeUri: String, albumId: String): List<Chapter> = withContext(io) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        var target: DocumentFile? = null
        if (root != null) {
            val pending = ArrayDeque<Pair<DocumentFile, Int>>()
            pending.add(root to 0)
            while (pending.isNotEmpty() && target == null) {
                val (dir, depth) = pending.removeFirst()
                if (dir.uri.toString() == albumId) {
                    target = dir
                } else if (depth < 3) {
                    for (child in dir.listFiles()) {
                        if (child.isDirectory) pending.add(child to depth + 1)
                    }
                }
            }
        }
        val folder = target
        if (folder == null) {
            emptyList()
        } else {
            folder.listFiles()
                .filter { it.isFile && isAudioFile(it) }
                .sortedBy { (it.name ?: "").lowercase() }
                .mapIndexed { i, f ->
                    Chapter(i, (f.name ?: "Track ${i + 1}").substringBeforeLast('.'), f.uri.toString())
                }
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val name = file.name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in audioExtensions) return true
        val type = file.type
        return type != null && type.startsWith("audio/")
    }

    // --- Broadcast auto-DJ track pool ---

    @Volatile private var poolCache: Pair<String, List<BroadcastTrack>>? = null
    private val trackNumPrefix = Regex("^\\s*\\d{1,3}\\s*[-._)]+\\s*")

    /**
     * Every audio file under [treeUri], for the Broadcast auto-DJ.
     *
     * The old approach (`folderAlbums` then `folderTracks` per album) re-walked
     * the whole SAF tree N times — ~30 s on a real library. This walks it once,
     * queries each directory with a single `DocumentsContract` cursor (name +
     * MIME for every child in one IPC, vs. `DocumentFile`'s per-child queries),
     * and fans the directory queries out with bounded concurrency.
     * Result cached per tree URI.
     */
    suspend fun broadcastPool(treeUri: String): List<BroadcastTrack> = withContext(io) {
        poolCache?.let { (uri, tracks) -> if (uri == treeUri && tracks.isNotEmpty()) return@withContext tracks }
        val t0 = System.currentTimeMillis()
        val tree = Uri.parse(treeUri)
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return@withContext emptyList()
        val rootName = queryDisplayName(tree, rootDocId).orEmpty()

        val out = ConcurrentLinkedQueue<BroadcastTrack>()
        val dirCount = AtomicInteger()
        val gate = Semaphore(8) // don't hammer the documents provider with 64 IO threads

        suspend fun walk(docId: String, depth: Int, folderName: String, parentName: String) {
            dirCount.incrementAndGet()
            val artist = if (parentName.isNotBlank() && parentName != rootName) parentName else folderName
            val children = gate.withPermit { queryChildren(tree, docId) }
            coroutineScope {
                for (ch in children) {
                    if (ch.isDir) {
                        if (depth < 3) launch { walk(ch.docId, depth + 1, ch.name, folderName) }
                    } else if (isAudio(ch.name, ch.mime)) {
                        val raw = ch.name.substringBeforeLast('.')
                        out.add(
                            BroadcastTrack(
                                uri = DocumentsContract.buildDocumentUriUsingTree(tree, ch.docId).toString(),
                                title = raw.replace(trackNumPrefix, "").trim().ifEmpty { raw },
                                artist = artist,
                                album = folderName,
                            ),
                        )
                    }
                }
            }
        }

        walk(rootDocId, 0, rootName.ifEmpty { "Music" }, "")
        val list = out.toList()
        Log.d(
            "MusicRepository",
            "broadcastPool: ${list.size} tracks / ${dirCount.get()} dirs in ${System.currentTimeMillis() - t0}ms",
        )
        poolCache = treeUri to list
        list
    }

    private data class SafChild(val docId: String, val name: String, val mime: String) {
        val isDir: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private fun queryChildren(tree: Uri, parentDocId: String): List<SafChild> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val out = ArrayList<SafChild>()
        runCatching {
            context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    out.add(SafChild(id, c.getString(1).orEmpty(), c.getString(2).orEmpty()))
                }
            }
        }
        return out
    }

    private fun queryDisplayName(tree: Uri, docId: String): String? = runCatching {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        context.contentResolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    private fun isAudio(name: String, mime: String): Boolean {
        if (mime.startsWith("audio/")) return true
        return name.substringAfterLast('.', "").lowercase() in audioExtensions
    }
}
