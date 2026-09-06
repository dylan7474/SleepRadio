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

/**
 * Loads cover art for the current item off the main thread. No image library —
 * [android.content.ContentResolver.loadThumbnail] for MediaStore album art and
 * [MediaMetadataRetriever.getEmbeddedPicture] for cover art embedded in local
 * music / audiobook files. Returns null (→ caller shows its placeholder) for
 * radio and anything without art.
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

private fun loadArt(context: Context, artworkUri: Uri?, mediaUri: Uri?): Bitmap? {
    if (artworkUri != null) {
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
