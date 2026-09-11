package org.dylanjones.sleepradio.core.data

/** Number of assignable source-preset slots on the player. */
const val PRESET_COUNT = 4

/** What a preset slot points at. See RETROSYNC_PLAN.md section 2. */
enum class SourceType { ALBUM, MUSIC_FOLDER, AUDIOBOOK, RADIO, BROADCAST, PODCAST }

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
 *  - ALBUM        -> MediaStore album id (as a string)
 *  - MUSIC_FOLDER -> folder-album document URI
 *  - AUDIOBOOK    -> document-tree book path (Phase 4 chunk C)
 *  - RADIO        -> stream URL
 *  - BROADCAST    -> the literal "broadcast" (auto-DJ over the music folder)
 *  - PODCAST      -> a subscribed feed's [PodcastFeed.id] (Phase 17); tapping
 *                    resolves the episode to play via [pickPodcastEpisode]
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
        /** Curated starter set; the user adds their own via manual entry or the
         *  online directory (Radio Browser). BBC HLS "pool" ids rotate every few
         *  months — if Radio 4 stops, re-add it from the directory. */
        val bundled: List<RadioStation> = listOf(
            RadioStation(
                id = "bbc_radio_4",
                name = "BBC Radio 4",
                streamUrl = "http://as-hls-ww-live.akamaized.net/pool_55057080/live/ww/bbc_radio_fourfm/bbc_radio_fourfm.isml/bbc_radio_fourfm-audio=320000.norewind.m3u8",
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

// --- Podcasts (Phase 17) ---------------------------------------------------

/** A subscribed (or search-result) podcast show. [id] is derived from
 *  [feedUrl] via [podcastFeedId] so the same feed always maps to the same id,
 *  whether it was found via the directory or added by URL. */
data class PodcastFeed(
    val id: String,
    val feedUrl: String,
    val title: String,
    val artworkUrl: String? = null,
)

/** One episode parsed live from a feed's RSS — not persisted (Decision #20). */
data class PodcastEpisode(
    val guid: String,
    val title: String,
    val audioUrl: String,
    val pubDateMs: Long? = null,
    val durationMs: Long? = null,
    val description: String = "",
)

/** Where playback of one episode should resume, and whether it's been heard. */
data class PodcastProgress(
    val episodeGuid: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)

/** Stable id for a podcast feed, derived from its URL (case/trailing-slash
 *  insensitive so the same show found two different ways still matches). */
fun podcastFeedId(feedUrl: String): String {
    val normalised = feedUrl.trim().trimEnd('/').lowercase()
    return "pod_" + normalised.hashCode().toUInt().toString(16)
}

/**
 * Which episode a PODCAST preset should play on tap, and how far into it to
 * seek: the most-recently-touched in-progress (not completed) episode if
 * there is one, else the newest episode that isn't marked completed, else —
 * everything's been heard — just the newest episode from the top. [episodes]
 * must already be newest-first (as [org.dylanjones.sleepradio.media
 * .PodcastRepository] returns them). Null only when [episodes] is empty.
 */
fun pickPodcastEpisode(
    episodes: List<PodcastEpisode>,
    progress: Map<String, PodcastProgress>,
): Pair<PodcastEpisode, Long>? {
    if (episodes.isEmpty()) return null
    val resuming = progress.values
        .filter { !it.completed && it.positionMs > 0L }
        .maxByOrNull { it.updatedAt }
        ?.let { p -> episodes.firstOrNull { it.guid == p.episodeGuid } }
    if (resuming != null) return resuming to progress.getValue(resuming.guid).positionMs
    val next = episodes.firstOrNull { progress[it.guid]?.completed != true } ?: episodes.first()
    return next to (progress[next.guid]?.positionMs ?: 0L)
}
