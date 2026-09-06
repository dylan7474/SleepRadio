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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.dylanjones.sleepradio.core.audio.AudioChannel
import org.dylanjones.sleepradio.core.audio.MixerController
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
    @ApplicationContext context: Context,
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

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushSnapshot()
            if (player.isPlaying) startTicker() else stopTicker()
        }
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            controller = f.get().apply { addListener(listener) }
            applyMainGain()
            pushSnapshot()
        }, ContextCompat.getMainExecutor(context))

        // Apply VOL / BAL to Channel A's output whenever the mixer changes.
        mixer.state.onEach { applyMainGain() }.launchIn(scope)
    }

    private fun applyMainGain() {
        val gain = mixer.state.value.effectiveGain(AudioChannel.MAIN)
        controller?.volume = gain.coerceIn(0f, 1f)
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        isRadio = false
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, /* startPositionMs = */ 0L)
        c.prepare()
        c.play()
    }

    /** Stream an internet-radio station on Channel A (live, no seek). */
    fun playRadio(url: String, name: String, description: String) {
        val c = controller ?: return
        isRadio = true
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(description)
                    .setStation(name)
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
        _state.value = PlaybackState(
            isConnected = true,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            isRadio = isRadio,
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
