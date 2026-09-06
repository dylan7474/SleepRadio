package org.dylanjones.sleepradio.media

import android.net.Uri

/** A single audio track from the on-device MediaStore. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val durationMs: Long,
    val trackNumber: Int,
    val contentUri: Uri,
    val artworkUri: Uri?,
)
