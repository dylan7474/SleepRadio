package org.dylanjones.sleepradio.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import androidx.media3.common.PlaybackParameters
import org.dylanjones.sleepradio.core.audio.AudioChannel
import org.dylanjones.sleepradio.core.audio.BinauralPreset
import org.dylanjones.sleepradio.core.audio.MixerController
import org.dylanjones.sleepradio.core.data.Chapter
import org.dylanjones.sleepradio.di.MainDispatcher
import org.dylanjones.sleepradio.media.Track
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of Channel A playback for the UI. */
data class PlaybackState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isRadio: Boolean = false,
    val isAudiobook: Boolean = false,
    val bookId: String? = null,
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    /** Radio: stable station name (never overwritten by stream metadata). */
    val stationName: String? = null,
    /** Radio: current "Artist - Track" from ICY stream metadata, if any. */
    val nowPlaying: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: Uri? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queueSize: Int = 0,
)

/**
 * Owns a [MediaController] bound to [PlaybackService] and exposes its state as a
 * [StateFlow]. All controller access happens on the main thread.
 */
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
    private val mixer: MixerController,
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null
    private var isRadio: Boolean = false
    private var isAudiobook: Boolean = false
    private var bookId: String? = null
    /** Stable station name from the slot label; never overwritten by ICY metadata. */
    private var radioStationName: String? = null
    /** Station description (fallback "now playing" line when the stream sends no metadata). */
    private var radioStationDesc: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushSnapshot()
            if (player.isPlaying) startTicker() else stopTicker()
        }
    }

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val f = MediaController.Builder(appContext, token).buildAsync()
        future = f
        f.addListener({
            controller = f.get().apply { addListener(listener) }
            applyMainGain()
            pushSnapshot()
        }, ContextCompat.getMainExecutor(appContext))

        // Apply VOL / BAL to Channel A's output whenever the mixer changes.
        mixer.state.onEach { applyMainGain() }.launchIn(scope)

        // Bring up the ambient foreground service whenever Channel B or C turns
        // on; it owns the noise/binaural generators and stops itself when both
        // go quiet (so ambient outlives Channel A and an app-swipe).
        mixer.ambient
            .map { it.noiseEnabled || it.binaural != BinauralPreset.OFF }
            .distinctUntilChanged()
            .onEach { anyAmbientOn -> if (anyAmbientOn) AmbientPlaybackService.start(appContext) }
            .launchIn(scope)
    }

    private fun applyMainGain() {
        val gain = mixer.state.value.effectiveGain(AudioChannel.MAIN)
        controller?.volume = gain.coerceIn(0f, 1f)
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        isRadio = false
        isAudiobook = false
        bookId = null
        c.setPlaybackParameters(PlaybackParameters(1f))
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, /* startPositionMs = */ 0L)
        c.prepare()
        c.play()
    }

    /** Play a folder-album: its audio files as an ordinary queue. */
    fun playFolderAlbum(files: List<Chapter>, albumTitle: String) {
        val c = controller ?: return
        if (files.isEmpty()) return
        isRadio = false
        isAudiobook = false
        bookId = null
        c.setPlaybackParameters(PlaybackParameters(1f))
        val items = files.map { f ->
            MediaItem.Builder()
                .setUri(f.uri)
                .setMediaId(f.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(f.title)
                        .setArtist(albumTitle)
                        .setAlbumTitle(albumTitle)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build(),
                )
                .build()
        }
        c.setMediaItems(items, 0, 0L)
        c.prepare()
        c.play()
    }

    /** Play an audiobook: chapters as a queue, restoring [startChapter] / [startPositionMs]. */
    fun playAudiobook(
        book: String,
        chapters: List<Chapter>,
        bookTitle: String,
        startChapter: Int,
        startPositionMs: Long,
    ) {
        val c = controller ?: return
        if (chapters.isEmpty()) return
        isRadio = false
        isAudiobook = true
        bookId = book
        val items = chapters.map { ch ->
            MediaItem.Builder()
                .setUri(ch.uri)
                .setMediaId(ch.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(ch.title)
                        .setArtist(bookTitle)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build(),
                )
                .build()
        }
        c.setPlaybackParameters(PlaybackParameters(1f))
        c.setMediaItems(items, startChapter.coerceIn(0, items.lastIndex), startPositionMs.coerceAtLeast(0L))
        c.prepare()
        c.play()
    }

    /** Seek by [deltaMs] (negative = backward), clamped at 0. Used by the
     *  transport prev/next buttons while an audiobook plays (±1 min). */
    fun skipBy(deltaMs: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        c.seekTo(target)
    }

    /** Stream an internet-radio station on Channel A (live, no seek). */
    fun playRadio(url: String, name: String, description: String) {
        val c = controller ?: return
        isRadio = true
        isAudiobook = false
        bookId = null
        radioStationName = name
        radioStationDesc = description.ifBlank { null }
        c.setPlaybackParameters(PlaybackParameters(1f))
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    // No .setTitle(): leave mediaMetadata.title free so Media3 can
                    // fill it from the stream's ICY StreamTitle. .setStation() keeps
                    // the station name stable; .setDisplayTitle() feeds the
                    // system media notification.
                    .setStation(name)
                    .setDisplayTitle(name)
                    .setArtist(description)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem() ?: Unit

    fun previous() = controller?.seekToPreviousMediaItem() ?: Unit

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                pushSnapshot()
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun pushSnapshot() {
        val c = controller
        if (c == null) {
            _state.value = PlaybackState(isConnected = false)
            return
        }
        val md = c.mediaMetadata
        // Radio: mediaMetadata.title carries the ICY StreamTitle (Media3 fills it
        // from the stream). displayTitle stays the station name for the notification.
        val icyNowPlaying = if (isRadio) cleanIcy(md.title?.toString()) else null
        _state.value = PlaybackState(
            isConnected = true,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            isRadio = isRadio,
            isAudiobook = isAudiobook,
            bookId = bookId,
            chapterIndex = if (isAudiobook) c.currentMediaItemIndex else 0,
            chapterCount = if (isAudiobook) c.mediaItemCount else 0,
            stationName = if (isRadio) radioStationName else null,
            nowPlaying = icyNowPlaying,
            title = md.title?.toString(),
            artist = md.artist?.toString(),
            artworkUri = md.artworkUri,
            positionMs = if (isRadio) 0L else c.currentPosition.coerceAtLeast(0L),
            durationMs = if (isRadio) 0L else c.duration.let { if (it > 0) it else 0L },
            hasNext = !isRadio && c.hasNextMediaItem(),
            hasPrevious = !isRadio && c.hasPreviousMediaItem(),
            queueSize = c.mediaItemCount,
        )
    }

    /**
     * Tidy an ICY StreamTitle into something presentable. Shoutcast/Icecast
     * titles are wildly inconsistent — "Artist - Track", bare track names,
     * "Unknown", ad markers, stray whitespace. Returns null when there's
     * nothing worth showing (blank, a placeholder, or just the station name).
     */
    private fun cleanIcy(raw: String?): String? {
        val collapsed = raw?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (collapsed.isEmpty()) return null
        val lower = collapsed.lowercase()
        if (lower == "unknown" || lower == "unknown - unknown" || lower == "-") return null
        // Split "Artist - Track" and drop halves that are empty / "unknown".
        val parts = collapsed.split(" - ").map { it.trim() }.filter {
            it.isNotEmpty() && !it.equals("unknown", ignoreCase = true)
        }
        val text = if (parts.isEmpty()) collapsed else parts.joinToString(" - ")
        if (text.equals(radioStationName?.trim(), ignoreCase = true)) return null
        return text
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
    }
}

private fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(albumTitle)
                .setArtworkUri(artworkUri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()
