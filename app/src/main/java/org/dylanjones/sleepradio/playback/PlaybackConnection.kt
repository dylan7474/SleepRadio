package org.dylanjones.sleepradio.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import org.dylanjones.sleepradio.core.broadcast.BroadcastConfig
import org.dylanjones.sleepradio.core.broadcast.BroadcastSelector
import org.dylanjones.sleepradio.core.broadcast.BroadcastTrack
import org.dylanjones.sleepradio.core.broadcast.DjScriptBuilder
import org.dylanjones.sleepradio.core.broadcast.LinkKind
import org.dylanjones.sleepradio.core.broadcast.ShowClock
import org.dylanjones.sleepradio.core.broadcast.WindDownPhase
import org.dylanjones.sleepradio.core.data.Chapter
import org.dylanjones.sleepradio.core.tts.DjVoicePlayer
import org.dylanjones.sleepradio.core.tts.OfflineTtsEngine
import org.dylanjones.sleepradio.core.tts.VoicePack
import org.dylanjones.sleepradio.di.MainDispatcher
import org.dylanjones.sleepradio.media.Track
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of Channel A playback for the UI. */
data class PlaybackState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isRadio: Boolean = false,
    val isAudiobook: Boolean = false,
    /** Auto-DJ "SleepRadio broadcast" source is running on Channel A. */
    val isBroadcast: Boolean = false,
    /** A DJ voice link is playing right now (broadcast only). */
    val djSpeaking: Boolean = false,
    val bookId: String? = null,
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    /** Radio: stable station name (never overwritten by stream metadata). */
    val stationName: String? = null,
    /** Radio: current "Artist - Track" from ICY stream metadata, if any. */
    val nowPlaying: String? = null,
    val title: String? = null,
    val artist: String? = null,
    /** Embedded/known artwork URI from mediaMetadata, if any. */
    val artworkUri: Uri? = null,
    /** The current item's own content URI (used to pull embedded cover art). */
    val mediaUri: Uri? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queueSize: Int = 0,
)

/** Sleep-timer snapshot for the UI. Ambient channels (B/C) are never affected. */
data class SleepTimerState(
    val active: Boolean = false,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
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

    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null
    /** 1f normally; ramped 1→0 by the sleep timer's fade, multiplied into Channel A gain. */
    private var sleepFade: Float = 1f
    /** 1f normally; reserved for ducking music under a DJ link (Chunk D talk-over). */
    private var duckGain: Float = 1f

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null
    private var isRadio: Boolean = false
    private var isAudiobook: Boolean = false
    private var bookId: String? = null

    // --- Broadcast (auto-DJ) state ---
    private var isBroadcast: Boolean = false
    private var djSpeaking: Boolean = false
    /** True while an END-of-track is being handled, so onEvents doesn't re-enter. */
    private var advancing: Boolean = false
    private var selector: BroadcastSelector? = null
    private var showClock: ShowClock? = null
    private var scriptBuilder: DjScriptBuilder? = null
    private var voicePack: VoicePack? = null
    private var ttsEngine: OfflineTtsEngine? = null
    private var djPlayer: DjVoicePlayer? = null
    private var currentBroadcast: BroadcastTrack? = null
    private var nextBroadcast: BroadcastTrack? = null
    /** The in-flight "load voice + speak welcome" coroutine, so a restart cancels it. */
    private var broadcastJob: Job? = null
    /** Link kind to play after the current track; NONE = straight into the next. */
    private var pendingLink: LinkKind = LinkKind.NONE
    private var linkPreloaded: Boolean = false
    /** Stable station name from the slot label; never overwritten by ICY metadata. */
    private var radioStationName: String? = null
    /** Station description (fallback "now playing" line when the stream sends no metadata). */
    private var radioStationDesc: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (isBroadcast && !advancing && player.playbackState == Player.STATE_ENDED) {
                onBroadcastTrackEnded()
            }
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
        val gain = mixer.state.value.effectiveGain(AudioChannel.MAIN) * sleepFade * duckGain
        controller?.volume = gain.coerceIn(0f, 1f)
    }

    // --- Sleep timer (Channel A only; B/C keep playing) ---

    /** Start / restart the sleep timer. Fades out and pauses Channel A on expiry. */
    fun startSleepTimer(durationMs: Long) {
        if (durationMs <= 0L) return
        cancelSleepTimer()
        val endAt = SystemClock.elapsedRealtime() + durationMs
        _sleepTimer.value = SleepTimerState(active = true, remainingMs = durationMs, totalMs = durationMs)
        sleepJob = scope.launch {
            while (true) {
                val remaining = endAt - SystemClock.elapsedRealtime()
                if (remaining <= SLEEP_FADE_MS) break
                _sleepTimer.value = _sleepTimer.value.copy(remainingMs = remaining)
                delay(500)
            }
            // Equal-ish linear fade of Channel A over the last SLEEP_FADE_MS.
            val fadeStart = SystemClock.elapsedRealtime()
            while (true) {
                val f = ((SystemClock.elapsedRealtime() - fadeStart).toFloat() / SLEEP_FADE_MS).coerceIn(0f, 1f)
                sleepFade = 1f - f
                applyMainGain()
                _sleepTimer.value = _sleepTimer.value.copy(
                    remainingMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L),
                )
                if (f >= 1f) break
                delay(100)
            }
            controller?.pause()
            sleepFade = 1f
            applyMainGain()
            _sleepTimer.value = SleepTimerState()
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepFade = 1f
        applyMainGain()
        _sleepTimer.value = SleepTimerState()
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        endBroadcastInternal()
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
        endBroadcastInternal()
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
        endBroadcastInternal()
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
        endBroadcastInternal()
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

    fun next() {
        if (isBroadcast) {
            // Skip straight to a freshly picked track (drop any pending link).
            advancing = true
            djPlayer?.stop()
            djSpeaking = false
            pendingLink = LinkKind.NONE
            linkPreloaded = false
            advanceBroadcast()
        } else {
            controller?.seekToNextMediaItem()
        }
    }

    fun previous() {
        if (isBroadcast) controller?.seekTo(0) else controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    // --- Broadcast (auto-DJ) ---

    /**
     * Start "SleepRadio broadcast" on Channel A: a self-selecting rotation over
     * [tracks], with the DJ ([voice]) reading a short link in the gap before some
     * tracks. [voice] == null → music only, no links.
     */
    fun startBroadcast(tracks: List<BroadcastTrack>, voice: VoicePack?, config: BroadcastConfig) {
        controller ?: return
        endBroadcastInternal()
        if (tracks.isEmpty()) return

        isRadio = false
        isAudiobook = false
        bookId = null
        isBroadcast = true
        selector = BroadcastSelector(tracks)
        showClock = ShowClock(config)
        scriptBuilder = DjScriptBuilder()
        voicePack = voice

        currentBroadcast = selector?.next()
        nextBroadcast = selector?.next()
        val first = currentBroadcast ?: run { endBroadcastInternal(); return }

        if (voice != null) {
            if (ttsEngine == null) ttsEngine = OfflineTtsEngine()
            if (djPlayer == null) djPlayer = DjVoicePlayer(ttsEngine!!)
            val engine = ttsEngine!!
            val player = djPlayer!!
            val builder = scriptBuilder!!
            djSpeaking = true
            // Load the voice, speak the welcome, THEN start the first track.
            broadcastJob = scope.launch {
                val ready = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    engine.ensureLoaded(voice) && player.preload(builder.welcome())
                }
                if (!isBroadcast || !isActive) return@launch // restarted / switched away
                if (ready) {
                    Log.d(TAG, "broadcast: welcome link")
                    player.playPreloaded(mixer.state.value.masterGain) {
                        djSpeaking = false
                        playSingleBroadcast(first)
                        onBroadcastTrackStarted()
                    }
                } else {
                    djSpeaking = false
                    playSingleBroadcast(first)
                    onBroadcastTrackStarted()
                }
            }
        } else {
            playSingleBroadcast(first)
            onBroadcastTrackStarted()
        }
    }

    private fun playSingleBroadcast(t: BroadcastTrack) {
        val c = controller ?: return
        c.setPlaybackParameters(PlaybackParameters(1f))
        c.setMediaItem(
            MediaItem.Builder()
                .setUri(t.uri)
                .setMediaId(t.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setAlbumTitle(t.album)
                        .setStation("SleepRadio")
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build(),
                )
                .build(),
        )
        c.prepare()
        c.play()
    }

    /** NORMAL, or wind the DJ down once the sleep timer is running. */
    private fun windDownPhase(): WindDownPhase {
        val st = _sleepTimer.value
        if (!st.active) return WindDownPhase.NORMAL
        return if (st.remainingMs > WINDDOWN_SILENT_MS) WindDownPhase.EASING else WindDownPhase.SILENT
    }

    /** After a track starts: decide the link that plays when it ends, pre-synth it. */
    private fun onBroadcastTrackStarted() {
        val phase = windDownPhase()
        val kind = showClock?.onTrackStarted(LocalTime.now(), phase) ?: LinkKind.NONE
        pendingLink = kind
        linkPreloaded = false
        Log.d(TAG, "broadcast: now '${currentBroadcast?.title}' by ${currentBroadcast?.artist}; " +
            "link after this track = $kind (windDown=$phase)")
        val player = djPlayer
        val builder = scriptBuilder
        if (kind != LinkKind.NONE && voicePack != null && player != null && builder != null) {
            val prev = currentBroadcast
            val next = nextBroadcast
            val terse = phase == WindDownPhase.EASING
            scope.launch(Dispatchers.Default) {
                val text = builder.build(kind, prev, next, LocalTime.now(), terse)
                if (text.isNotBlank()) {
                    linkPreloaded = player.preload(text)
                    Log.d(TAG, "broadcast: link preloaded=$linkPreloaded — \"$text\"")
                }
            }
        }
    }

    /** Channel A hit STATE_ENDED during a broadcast. */
    private fun onBroadcastTrackEnded() {
        advancing = true
        val player = djPlayer
        if (pendingLink != LinkKind.NONE && linkPreloaded && player != null) {
            djSpeaking = true
            pushSnapshot()
            Log.d(TAG, "broadcast: track ended → playing $pendingLink link")
            player.playPreloaded(mixer.state.value.masterGain) {
                djSpeaking = false
                Log.d(TAG, "broadcast: link done → next track")
                advanceBroadcast()
            }
        } else {
            Log.d(TAG, "broadcast: track ended → next track (no link: " +
                "kind=$pendingLink preloaded=$linkPreloaded)")
            advanceBroadcast()
        }
    }

    /** Move to the pre-picked next track and pick a new one behind it. */
    private fun advanceBroadcast() {
        if (!isBroadcast) { advancing = false; return }
        val upcoming = nextBroadcast ?: selector?.next()
        if (upcoming == null) { endBroadcastInternal(); return }
        currentBroadcast = upcoming
        nextBroadcast = selector?.next()
        playSingleBroadcast(upcoming)
        onBroadcastTrackStarted()
        advancing = false
    }

    /** Tear down broadcast state. Safe to call when not broadcasting. */
    private fun endBroadcastInternal() {
        if (!isBroadcast && selector == null) return
        val wasBroadcasting = isBroadcast
        isBroadcast = false
        djSpeaking = false
        advancing = false
        pendingLink = LinkKind.NONE
        linkPreloaded = false
        broadcastJob?.cancel()
        broadcastJob = null
        selector = null
        showClock = null
        currentBroadcast = null
        nextBroadcast = null
        voicePack = null
        duckGain = 1f
        djPlayer?.stop()
        djPlayer?.clearCache()
        // Halt any leftover Channel-A playback so a restart's welcome doesn't
        // talk over the previous run's track.
        if (wasBroadcasting) runCatching { controller?.pause() }
        val engine = ttsEngine
        if (engine != null) scope.launch(Dispatchers.Default) { engine.release() }
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
            isBroadcast = isBroadcast,
            djSpeaking = djSpeaking,
            bookId = bookId,
            chapterIndex = if (isAudiobook) c.currentMediaItemIndex else 0,
            chapterCount = if (isAudiobook) c.mediaItemCount else 0,
            stationName = if (isRadio) radioStationName else if (isBroadcast) "SleepRadio" else null,
            nowPlaying = icyNowPlaying,
            title = md.title?.toString(),
            artist = md.artist?.toString(),
            artworkUri = md.artworkUri,
            mediaUri = if (isRadio) {
                null
            } else {
                c.currentMediaItem?.localConfiguration?.uri
                    ?: c.currentMediaItem?.mediaId?.takeIf { it.contains("://") }?.let(Uri::parse)
            },
            positionMs = if (isRadio) 0L else c.currentPosition.coerceAtLeast(0L),
            durationMs = if (isRadio) 0L else c.duration.let { if (it > 0) it else 0L },
            hasNext = isBroadcast || (!isRadio && c.hasNextMediaItem()),
            hasPrevious = isBroadcast || (!isRadio && c.hasPreviousMediaItem()),
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
        const val TAG = "PlaybackConnection"
        const val POSITION_POLL_MS = 500L
        const val SLEEP_FADE_MS = 20_000L
        /** Broadcast: with less than this left on the sleep timer, the DJ goes silent. */
        const val WINDDOWN_SILENT_MS = 5 * 60_000L
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
