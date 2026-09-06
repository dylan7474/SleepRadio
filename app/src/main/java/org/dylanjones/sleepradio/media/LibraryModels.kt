package org.dylanjones.sleepradio.media

import android.net.Uri

/** An album from the on-device MediaStore. */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val artworkUri: Uri?,
)

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
