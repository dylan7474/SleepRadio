package org.dylanjones.sleepradio.core.data

/** Number of assignable source-preset slots on the player. */
const val PRESET_COUNT = 4

/** What a preset slot points at. See RETROSYNC_PLAN.md section 2. */
enum class SourceType { ALBUM, MUSIC_FOLDER, AUDIOBOOK, RADIO }

/** An "album" discovered by walking a SAF-granted music folder (any folder that
 *  directly contains audio files). [id] is that folder's document URI string. */
data class FolderAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val trackCount: Int,
)

/**
 * A resolved preset slot. [refId] is interpreted by [type]:
 *  - ALBUM      -> MediaStore album id (as a string)
 *  - AUDIOBOOK  -> document-tree book path (Phase 4 chunk C)
 *  - RADIO      -> stream URL
 */
data class SourceSlot(
    val index: Int,
    val type: SourceType,
    val refId: String,
    val label: String,
    val sublabel: String,
    val artworkUri: String? = null,
)

/** A local audiobook — a folder of chapter files, or a single file. */
data class Audiobook(
    /** Stable id = the book's document/tree-child URI string. */
    val id: String,
    val title: String,
    val chapterCount: Int,
)

/** One chapter (audio file) of an audiobook. */
data class Chapter(
    val index: Int,
    val title: String,
    val uri: String,
)

/** Where playback of an audiobook should resume. */
data class AudiobookProgress(
    val chapterIndex: Int,
    val positionMs: Long,
)

/** An internet-radio station. */
data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val description: String,
) {
    companion object {
        /** Curated starter set. User-added stations come in a later chunk. */
        val bundled: List<RadioStation> = listOf(
            RadioStation(
                id = "bbc_radio_4",
                name = "BBC Radio 4",
                streamUrl = "http://as-hls-ww-live.akamaized.net/pool_904/live/ww/bbc_radio_fourfm/bbc_radio_fourfm.isml/bbc_radio_fourfm-audio%3d96000.norewind.m3u8",
                description = "Speech, news & drama",
            ),
            RadioStation(
                id = "bbc_world_service",
                name = "BBC World Service",
                streamUrl = "http://stream.live.vc.bbcmedia.co.uk/bbc_world_service",
                description = "International news",
            ),
            RadioStation(
                id = "radio_paradise_main",
                name = "Radio Paradise",
                streamUrl = "http://stream.radioparadise.com/aac-320",
                description = "Eclectic DJ-curated mix",
            ),
            RadioStation(
                id = "radio_paradise_mellow",
                name = "Radio Paradise — Mellow",
                streamUrl = "http://stream.radioparadise.com/mellow-320",
                description = "Softer, downtempo",
            ),
            RadioStation(
                id = "soma_groovesalad",
                name = "SomaFM — Groove Salad",
                streamUrl = "http://ice1.somafm.com/groovesalad-256-mp3",
                description = "Ambient / downtempo",
            ),
            RadioStation(
                id = "soma_dronezone",
                name = "SomaFM — Drone Zone",
                streamUrl = "http://ice1.somafm.com/dronezone-256-mp3",
                description = "Atmospheric textures for sleep",
            ),
        )
    }
}
