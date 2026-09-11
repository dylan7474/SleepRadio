package org.dylanjones.sleepradio.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads cover art for the current item off the main thread. No image library —
 * [android.content.ContentResolver.loadThumbnail] for MediaStore album art,
 * [MediaMetadataRetriever.getEmbeddedPicture] for cover art embedded in local
 * music / audiobook files, and a plain [HttpURLConnection] fetch (Phase 17) for
 * a podcast feed/episode's remote artwork URL. Returns null (→ caller shows
 * its placeholder) for radio and anything without art.
 */
@Composable
fun rememberAlbumArt(artworkUri: Uri?, mediaUri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    var art by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(artworkUri, mediaUri) {
        art = null
        art = withContext(Dispatchers.IO) {
            loadArt(context, artworkUri, mediaUri)?.asImageBitmap()
        }
    }
    return art
}

/** A small remote image by URL (podcast search-result / subscription-list
 *  thumbnails) — the same fetch + cache [loadArt] uses for a now-playing
 *  episode's artwork, exposed standalone for list rows. */
@Composable
fun rememberRemoteImage(url: String?): ImageBitmap? {
    var art by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        art = null
        if (url != null) {
            art = withContext(Dispatchers.IO) { loadRemoteArt(url)?.asImageBitmap() }
        }
    }
    return art
}

private fun loadArt(context: Context, artworkUri: Uri?, mediaUri: Uri?): Bitmap? {
    if (artworkUri != null) {
        if (artworkUri.scheme == "http" || artworkUri.scheme == "https") {
            return loadRemoteArt(artworkUri.toString())
        }
        runCatching {
            return context.contentResolver.loadThumbnail(artworkUri, Size(320, 320), null)
        }
    }
    if (mediaUri != null) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, mediaUri)
            val bytes = retriever.embeddedPicture ?: return null
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { retriever.release() }
        }
    }
    return null
}

/** Small in-memory LRU so switching episodes / scrolling a search list doesn't
 *  re-fetch art already seen this session. Not persisted across app kill —
 *  cheap enough to refetch, and this is display-only (no offline caching). */
private val remoteArtCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) = size > 24
}

private fun loadRemoteArt(url: String): Bitmap? {
    synchronized(remoteArtCache) { remoteArtCache[url] }?.let { return it }
    var conn: HttpURLConnection? = null
    val bitmap = try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        conn.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (_: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
    if (bitmap != null) synchronized(remoteArtCache) { remoteArtCache[url] = bitmap }
    return bitmap
}
