package org.dylanjones.sleepradio.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.dylanjones.sleepradio.core.data.Chapter
import org.dylanjones.sleepradio.core.data.FolderAlbum
import org.dylanjones.sleepradio.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads albums and tracks from the on-device [MediaStore]. Requires
 * `READ_MEDIA_AUDIO` (checked by the caller). SAF folder trees for music /
 * audiobook folders come in Phase 4.
 */
@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val albumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")

    suspend fun albums(): List<Album> = withContext(io) {
        // Query MediaStore.Audio.Media and group by album, rather than the
        // Audio.Albums collection (which some OEM/preview builds return empty).
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ARTIST,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val byId = LinkedHashMap<Long, MutableAlbum>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC",
            )?.use { c ->
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumArtistCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                while (c.moveToNext()) {
                    val albumId = c.getLong(albumIdCol)
                    val album = byId.getOrPut(albumId) {
                        MutableAlbum(
                            id = albumId,
                            title = c.getString(albumCol) ?: "Unknown album",
                            artist = (albumArtistCol.takeIf { it >= 0 }?.let { c.getString(it) }
                                ?: c.getString(artistCol) ?: "Unknown artist"),
                        )
                    }
                    album.trackCount++
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "albums() query failed", e)
        }
        Log.d(TAG, "albums(): ${byId.size} albums")
        byId.values.map {
            Album(
                id = it.id,
                title = it.title,
                artist = it.artist,
                trackCount = it.trackCount,
                artworkUri = ContentUris.withAppendedId(albumArtBase, it.id),
            )
        }
    }

    private class MutableAlbum(
        val id: Long,
        val title: String,
        val artist: String,
        var trackCount: Int = 0,
    )

    private companion object {
        const val TAG = "MusicRepository"
    }

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
}
