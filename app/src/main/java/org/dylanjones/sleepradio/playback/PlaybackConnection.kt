package org.dylanjones.sleepradio.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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
import kotlinx.coroutines.withContext
import androidx.media3.common.PlaybackParameters
import org.dylanjones.sleepradio.core.audio.AudioChannel
import org.dylanjones.sleepradio.core.audio.BinauralPreset
import org.dylanjones.sleepradio.core.audio.TrackProbe
import org.dylanjones.sleepradio.core.audio.MixerController
import org.dylanjones.sleepradio.core.broadcast.BroadcastConfig
import org.dylanjones.sleepradio.core.broadcast.BroadcastSelector
import org.dylanjones.sleepradio.core.broadcast.BroadcastTrack
import org.dylanjones.sleepradio.core.broadcast.DjScriptBuilder
import org.dylanjones.sleepradio.core.broadcast.JingleClip
import org.dylanjones.sleepradio.core.broadcast.LinkKind
import org.dylanjones.sleepradio.core.broadcast.ShowClock
import org.dylanjones.sleepradio.core.broadcast.WindDownPhase
import org.dylanjones.sleepradio.core.data.Chapter
import org.dylanjones.sleepradio.core.tts.DjVoicePlayer
import org.dylanjones.sleepradio.core.tts.OfflineTtsEngine
import org.dylanjones.sleepradio.core.tts.VoicePack
import org.dylanjones.sleepradio.di.MainDispatcher
import org.dylanjones.sleepradio.media.Track
import java.time.Duration
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
    /** A podcast episode is on Channel A (Phase 17) — on-demand + seekable,
     *  like an audiobook, but a single item (no chapters). */
    val isPodcast: Boolean = false,
    /** Subscribed feed id of the current podcast episode, for progress-saving. */
    val podcastFeedId: String? = null,
    /** Guid of the current podcast episode, for progress-saving. */
    val podcastEpisodeGuid: String? = null,
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

/** One step in a Broadcast segue: a spoken line, or a jingle sting. */
private sealed interface SegueStep {
    data class Say(val text: String) : SegueStep
    /** [uri] null = pull the next one from the shuffle; set = play this exact clip. */
    data class Jingle(val uri: String? = null) : SegueStep
}

/**
 * Owns a [MediaController] bound to [PlaybackService] and exposes its state as a
 * [StateFlow]. All controller access happens on the main thread.
 */
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    private val mixer: MixerController,
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    /** Phase 12/14: just-in-time scan (loudness gain + edge-silence trim) of the
     *  broadcast rotation, run a track ahead. */
    private val trackProbe = TrackProbe()

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
    private var isPodcast: Boolean = false
    private var podcastFeedId: String? = null
    private var podcastEpisodeGuid: String? = null
    private var podcastFeedTitle: String? = null

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
    /**
     * What plays in the gap AFTER the current track: an ordered list of spoken
     * lines and/or a jingle. Built (and its speech pre-synthesised) when the
     * track starts; drained by [runNextSegueStep] when it ends.
     */
    private var seguePlan: ArrayDeque<SegueStep> = ArrayDeque()
    /** True while a jingle MediaItem is on Channel A, so its STATE_ENDED continues the segue. */
    private var playingJingle: Boolean = false
    /** True once the gap's segue has started draining, so a late pre-synth can't refill it. */
    private var segueRunning: Boolean = false
    /** Bumped each track start / (re)start, so a stale pre-synth coroutine can't install its plan. */
    private var broadcastGen: Int = 0
    /** Maximum-chattiness: back-announce every track and keep time checks naming tracks. */
    private var announceEveryTrack: Boolean = false
    /** DJ voice level (0..1), applied on top of the master VOL. */
    private var announcerVolume: Float = 1f
    /** DJ speech rate (1.0 = natural, higher is faster). */
    private var announcerSpeed: Float = 1f
    /** Jingle folder contents (document URIs) + cadence; [jingleEvery] 0 = jingles off. */
    private var jingleUris: List<String> = emptyList()
    private var jingleEvery: Int = 0
    private var tracksSinceJingle: Int = 0
    /** Shuffled play order, refilled (avoiding an immediate repeat) when drained. */
    private val jingleQueue: ArrayDeque<String> = ArrayDeque()
    private var lastJingleUri: String? = null
    /** A short (< 45 s) jingle to open the show with, after the welcome; null = none. */
    private var startupJingleUri: String? = null
    /** The first track, held while the opening welcome/jingle plays. */
    private var startupFirst: BroadcastTrack? = null
    /** Consecutive broadcast tracks that failed to play; a full pool of duds stops the show. */
    private var broadcastErrorStreak: Int = 0
    /** Stable station name from the slot label; never overwritten by ICY metadata. */
    private var radioStationName: String? = null
    /** Station description (fallback "now playing" line when the stream sends no metadata). */
    private var radioStationDesc: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (isBroadcast && player.playbackState == Player.STATE_READY) {
                broadcastErrorStreak = 0 // this track loaded fine
            }
            if (isBroadcast && player.playbackState == Player.STATE_ENDED) {
                if (playingJingle) {
                    playingJingle = false
                    runNextSegueStep()
                } else if (!advancing) {
                    onBroadcastTrackEnded()
                }
            }
            pushSnapshot()
            if (player.isPlaying) startTicker() else stopTicker()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!isBroadcast) return
            // A bad jingle file must not freeze the segue — skip it and carry on.
            if (playingJingle) {
                Log.w(TAG, "broadcast: jingle failed (${error.errorCodeName}); skipping")
                playingJingle = false
                runNextSegueStep()
                return
            }
            // An unreadable/corrupt track in the broadcast pool would otherwise
            // stall the whole show (no STATE_ENDED ever arrives). Skip past it;
            // give up only if the pool is nothing but bad files.
            if (advancing) return
            broadcastErrorStreak++
            Log.w(TAG, "broadcast: '${currentBroadcast?.title}' failed to play " +
                "(${error.errorCodeName}); skipping (streak=$broadcastErrorStreak)")
            if (broadcastErrorStreak >= MAX_BROADCAST_ERROR_STREAK) {
                Log.w(TAG, "broadcast: too many failed tracks in a row — stopping")
                endBroadcastInternal()
            } else {
                advancing = true
                seguePlan.clear()
                advanceBroadcast()
            }
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

    // --- Broadcast loudness levelling (Phase 12) + edge-silence trim (Phase 14) ---

    /**
     * Set the sink's per-item gain ([MixerController.itemGain] → the
     * [org.dylanjones.sleepradio.core.audio.GainAudioProcessor]) for the
     * Channel-A item [uri] that's starting now. A cached measurement applies at
     * once; otherwise reset to unity (don't carry the previous item's gain) and
     * measure in the background, applying only if [uri] is still current when it
     * lands. Non-broadcast playback resets this to 1f via [endBroadcastInternal].
     */
    private fun applyItemGain(uri: String) {
        trackProbe.cached(uri)?.let { mixer.setItemGain(it.gain); return }
        mixer.setItemGain(1f)
        scope.launch(Dispatchers.Default) {
            val g = trackProbe.scanFor(appContext, uri).gain
            withContext(mainDispatcher) {
                if (isBroadcast && controller?.currentMediaItem?.mediaId == uri) mixer.setItemGain(g)
            }
        }
    }

    /** Pre-scan [uri] so [applyItemGain] / the clip config are ready when it plays. */
    private fun warmItemGain(uri: String?) {
        val u = uri ?: return
        scope.launch(Dispatchers.Default) { trackProbe.warm(appContext, u) }
    }

    /**
     * Phase 14: clip the broadcast track [uri] to its real musical start/end
     * from the pre-scan (trailing digital black, occasional leading silence), so
     * the DJ link lands right after the music. Null when nothing worth trimming
     * was found, the scan hasn't run yet (first track of a show), or [uri] is a
     * jingle. `STATE_ENDED` then fires at the clipped end → the existing segue.
     */
    private fun broadcastClip(uri: String): MediaItem.ClippingConfiguration? {
        val scan = trackProbe.cached(uri) ?: return null
        if (scan.startMs <= 0L && scan.endMs <= 0L) return null
        return MediaItem.ClippingConfiguration.Builder()
            .apply {
                if (scan.startMs > 0L) setStartPositionMs(scan.startMs)
                if (scan.endMs > 0L) setEndPositionMs(scan.endMs)
            }
            .build()
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
        endPodcastInternal()
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
        endPodcastInternal()
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
        endPodcastInternal()
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
     *  transport prev/next buttons while an audiobook or podcast plays (±1 min). */
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
        endPodcastInternal()
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

    /**
     * Stream a podcast episode on Channel A (Phase 17): on-demand + seekable,
     * like an audiobook, but a single item. [feedId]/[episodeGuid] are carried
     * in [PlaybackState] purely so the ViewModel can save resume progress;
     * playback itself needs only [audioUrl]. Resumes at [startPositionMs] if
     * given (the caller resolves that from
     * [org.dylanjones.sleepradio.core.data.db.PodcastProgressEntity]).
     */
    fun playPodcastEpisode(
        feedId: String,
        feedTitle: String,
        episodeGuid: String,
        title: String,
        audioUrl: String,
        artworkUrl: String?,
        startPositionMs: Long = 0L,
    ) {
        val c = controller ?: return
        endBroadcastInternal()
        isRadio = false
        isAudiobook = false
        bookId = null
        isPodcast = true
        podcastFeedId = feedId
        podcastEpisodeGuid = episodeGuid
        podcastFeedTitle = feedTitle
        c.setPlaybackParameters(PlaybackParameters(1f))
        val item = MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaId(episodeGuid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(feedTitle)
                    .setArtworkUri(artworkUrl?.let(Uri::parse))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
        c.setMediaItem(item, startPositionMs.coerceAtLeast(0L))
        c.prepare()
        c.play()
    }

    /** Clear podcast state. Safe to call when nothing was playing. */
    private fun endPodcastInternal() {
        isPodcast = false
        podcastFeedId = null
        podcastEpisodeGuid = null
        podcastFeedTitle = null
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        if (isBroadcast) {
            // Skip straight to a freshly picked track (drop the pending segue).
            advancing = true
            djPlayer?.stop()
            djSpeaking = false
            playingJingle = false
            segueRunning = false
            startupFirst = null
            seguePlan.clear()
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
     * tracks and — when [config].jingleEvery > 0 — a [jingles] sting mixed in.
     * [voice] == null → music (and jingles) only, no spoken links.
     */
    fun startBroadcast(
        tracks: List<BroadcastTrack>,
        voice: VoicePack?,
        jingles: List<JingleClip>,
        config: BroadcastConfig,
    ) {
        val c = controller ?: return
        endBroadcastInternal()
        if (tracks.isEmpty()) return
        // Silence whatever was on Channel A (e.g. an internet-radio stream) so
        // the welcome doesn't talk over it while the first track is prepared.
        runCatching { c.pause() }

        isRadio = false
        isAudiobook = false
        bookId = null
        endPodcastInternal()
        isBroadcast = true
        broadcastGen++
        selector = BroadcastSelector(tracks)
        showClock = ShowClock(config)
        scriptBuilder = DjScriptBuilder()
        announceEveryTrack = config.announceEveryTrack
        announcerVolume = config.announcerVolume.coerceIn(0f, 1f)
        announcerSpeed = config.announcerSpeed.coerceIn(0.5f, 2f)
        jingleUris = jingles.map { it.uri }
        jingleEvery = if (jingles.isEmpty()) 0 else config.jingleEvery.coerceIn(0, 10)
        tracksSinceJingle = 0
        jingleQueue.clear()
        lastJingleUri = null
        // Open the show with one of the shorter jingles (under 45 s), if there is one.
        startupJingleUri = jingles.filter { it.durationMs in 1 until STARTUP_JINGLE_MAX_MS }
            .map { it.uri }.shuffled().firstOrNull()
        voicePack = voice

        currentBroadcast = selector?.next()
        nextBroadcast = selector?.next()
        val first = currentBroadcast ?: run { endBroadcastInternal(); return }

        // Loudness-scan what opens the show while the voice/model loads (Phase 12).
        warmItemGain(first.uri)
        warmItemGain(startupJingleUri)
        warmItemGain(nextBroadcast?.uri)

        if (voice != null) {
            if (ttsEngine == null) ttsEngine = OfflineTtsEngine()
            if (djPlayer == null) {
            djPlayer = DjVoicePlayer(ttsEngine!!).apply { onLevel = mixer::reportDjPeak }
        }
            val engine = ttsEngine!!
            val player = djPlayer!!
            val builder = scriptBuilder!!
            // Opening sequence: welcome → (short jingle) → first-track intro → track 1.
            // Runs through the same segue machinery; startupFirst tells its tail
            // to play the first track instead of advancing to the next.
            djSpeaking = true
            advancing = true
            segueRunning = true
            startupFirst = first
            val gen = broadcastGen
            val opener = startupJingleUri
            broadcastJob = scope.launch {
                val loaded = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    engine.ensureLoaded(voice)
                }
                if (!isBroadcast || !isActive || gen != broadcastGen) return@launch
                val steps = ArrayList<SegueStep>()
                if (loaded && opener != null) {
                    steps += SegueStep.Say(builder.welcomeGreeting())
                    steps += SegueStep.Jingle(opener)
                    steps += SegueStep.Say(builder.welcomeFirstTrack(first))
                } else if (loaded) {
                    steps += SegueStep.Say(builder.welcome(first))
                }
                kotlinx.coroutines.withContext(Dispatchers.Default) {
                    steps.forEach { if (it is SegueStep.Say) player.preload(it.text, announcerSpeed) }
                }
                if (!isBroadcast || !isActive || gen != broadcastGen) return@launch
                Log.d(TAG, "broadcast: opening = " + steps.joinToString {
                    if (it is SegueStep.Say) "say(\"${it.text}\")" else "jingle"
                })
                seguePlan = ArrayDeque(steps)
                runNextSegueStep()
            }
        } else {
            playSingleBroadcast(first)
            onBroadcastTrackStarted()
        }
    }

    /**
     * Begin loading [pack] into the TTS engine now, so the eventual
     * [startBroadcast] doesn't stall on the ~1 s model load. Safe to call early
     * (e.g. while the track pool is still being scanned).
     */
    fun prewarmVoice(pack: VoicePack?) {
        if (pack == null) return
        if (ttsEngine == null) ttsEngine = OfflineTtsEngine()
        if (djPlayer == null) {
            djPlayer = DjVoicePlayer(ttsEngine!!).apply { onLevel = mixer::reportDjPeak }
        }
        val engine = ttsEngine!!
        scope.launch(Dispatchers.Default) { engine.ensureLoaded(pack) }
    }

    private fun playSingleBroadcast(t: BroadcastTrack) {
        val c = controller ?: return
        applyItemGain(t.uri)
        c.setPlaybackParameters(PlaybackParameters(1f))
        val builder = MediaItem.Builder()
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
        broadcastClip(t.uri)?.let { clip ->
            builder.setClippingConfiguration(clip)
            trackProbe.cached(t.uri)?.let {
                Log.d(TAG, "broadcast: clip '${t.title}' to [${it.startMs}..${it.endMs}]ms")
            }
        }
        c.setMediaItem(builder.build())
        c.prepare()
        c.play()
    }

    /** NORMAL, or wind the DJ down once the sleep timer is running. */
    private fun windDownPhase(): WindDownPhase {
        val st = _sleepTimer.value
        if (!st.active) return WindDownPhase.NORMAL
        return if (st.remainingMs > WINDDOWN_SILENT_MS) WindDownPhase.EASING else WindDownPhase.SILENT
    }

    /**
     * After a track starts: work out what fills the gap when it ends — a spoken
     * link, a jingle, or both (back-announce → jingle → next-track intro). The
     * plan is built and installed **synchronously** so a very short track can't
     * outrun it; a background pass then pre-synthesises the speech and, for a
     * time check, swaps in the clock projected to when it will actually be heard.
     */
    private fun onBroadcastTrackStarted() {
        val gen = ++broadcastGen
        val phase = windDownPhase()
        val kind = showClock?.onTrackStarted(LocalTime.now(), phase) ?: LinkKind.NONE
        val jingleDue = jingleDueThisGap(phase)
        Log.d(TAG, "broadcast: now '${currentBroadcast?.title}' by ${currentBroadcast?.artist}; " +
            "after this track: link=$kind jingle=$jingleDue (windDown=$phase)")
        // Scan the next track's loudness now, while this one plays (Phase 12).
        warmItemGain(nextBroadcast?.uri)

        val builder = scriptBuilder
        val player = djPlayer
        val hasVoice = voicePack != null && builder != null && player != null
        val prev = currentBroadcast
        val next = nextBroadcast
        val terse = phase == WindDownPhase.EASING
        val everyTrack = announceEveryTrack

        fun planFor(spokenAt: LocalTime): List<SegueStep> = buildList {
            val talkyKind = kind == LinkKind.LINK || kind == LinkKind.TIME_CHECK
            when {
                // Jingle with the DJ talking: always back-announce → jingle →
                // next-track intro. A time check folds into the back-announce.
                jingleDue && hasVoice && talkyKind -> {
                    val back = builder!!.outroLine(prev) +
                        if (kind == LinkKind.TIME_CHECK) " ${builder.timeLine(spokenAt)}" else ""
                    add(SegueStep.Say(back))
                    add(SegueStep.Jingle())
                    if (!terse) add(SegueStep.Say(builder.introLine(next)))
                }
                jingleDue -> add(SegueStep.Jingle()) // NONE / IDENT, or no voice
                hasVoice && kind != LinkKind.NONE -> {
                    builder!!.build(kind, prev, next, spokenAt, terse, everyTrack)
                        .takeIf { it.isNotBlank() }?.let { add(SegueStep.Say(it)) }
                }
            }
        }

        segueRunning = false
        val steps = planFor(LocalTime.now())
        seguePlan = ArrayDeque(steps)
        Log.d(TAG, "broadcast: segue = " + steps.joinToString {
            if (it is SegueStep.Say) "say(\"${it.text}\")" else "jingle"
        })
        if (steps.isEmpty()) return

        scope.launch(Dispatchers.Default) {
            // For a time check, redo the plan with the clock projected to when it
            // will actually be heard (past the track's remaining play time), then
            // pre-synthesise every spoken line so the segue plays gaplessly.
            val projected = if (kind == LinkKind.TIME_CHECK && hasVoice) {
                val ahead = awaitTrackRemainingMs()
                Log.d(TAG, "broadcast: time check projected ${ahead}ms ahead " +
                    "(${LocalTime.now()} -> ${LocalTime.now().plus(Duration.ofMillis(ahead))})")
                planFor(LocalTime.now().plus(Duration.ofMillis(ahead)))
            } else {
                null
            }
            val toSynth = projected ?: steps
            player?.let { p ->
                toSynth.forEach { if (it is SegueStep.Say) p.preload(it.text, announcerSpeed) }
            }
            if (projected != null) {
                withContext(mainDispatcher) {
                    // Only swap in the projected text if the segue hasn't started.
                    if (isBroadcast && gen == broadcastGen && !segueRunning) {
                        seguePlan = ArrayDeque(projected)
                    }
                }
            }
        }
    }

    /** Advance the jingle counter and say whether one lands in the next gap. */
    private fun jingleDueThisGap(phase: WindDownPhase): Boolean {
        if (jingleEvery <= 0 || jingleUris.isEmpty()) return false
        tracksSinceJingle++
        // Jingles are attention-grabbers — hold them once the sleep timer is winding down.
        if (phase != WindDownPhase.NORMAL) return false
        if (tracksSinceJingle < jingleEvery) return false
        tracksSinceJingle = 0
        return true
    }

    /** Next jingle to play: shuffled, never the same one twice running. */
    private fun nextJingleUri(): String? {
        if (jingleUris.isEmpty()) return null
        if (jingleQueue.isEmpty()) {
            val shuffled = jingleUris.shuffled().toMutableList()
            if (shuffled.size > 1 && shuffled.first() == lastJingleUri) {
                shuffled.add(shuffled.removeAt(0))
            }
            jingleQueue.addAll(shuffled)
        }
        return jingleQueue.removeFirst().also { lastJingleUri = it }
    }

    /**
     * Best-effort remaining play time of the current Channel-A track. Polls the
     * controller (on the main thread) until its duration resolves, up to ~2 s;
     * returns 0 if it never does (unknown length / not ready).
     */
    private suspend fun awaitTrackRemainingMs(): Long {
        // Prefer the length TrackProbe already decoded for this track: right
        // after prepare() the controller's duration is usually still TIME_UNSET,
        // and timing out here used to return 0 — so the time check was spoken as
        // of the track *start*, minutes stale.
        scannedRemainingMs()?.let {
            Log.d(TAG, "broadcast: remaining ${it}ms from scan")
            return it
        }
        repeat(15) {
            val remaining = withContext(mainDispatcher) {
                val c = controller ?: return@withContext -1L
                val d = c.duration
                if (d == C.TIME_UNSET || d <= 0L) -1L
                else (d - c.currentPosition).coerceAtLeast(0L)
            }
            if (remaining >= 0L) return remaining
            delay(200)
        }
        return 0L
    }

    /** Remaining play time of the current broadcast track from its pre-scan
     *  (clip-aware container duration minus the current position); null if the
     *  scan isn't cached or carries no usable duration. */
    private suspend fun scannedRemainingMs(): Long? {
        val uri = currentBroadcast?.uri ?: return null
        val playable = trackProbe.cached(uri)?.playableMs?.takeIf { it > 0L } ?: return null
        val pos = withContext(mainDispatcher) {
            controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
        }
        return (playable - pos).coerceAtLeast(0L)
    }

    /** Channel A hit STATE_ENDED during a broadcast: run the gap's segue, then advance. */
    private fun onBroadcastTrackEnded() {
        advancing = true
        segueRunning = true
        runNextSegueStep()
    }

    /** Play the next spoken line / jingle in [seguePlan]; when it's empty, move on. */
    private fun runNextSegueStep() {
        val step = if (seguePlan.isEmpty()) null else seguePlan.removeFirst()
        when (step) {
            null -> {
                val opening = startupFirst
                if (opening != null) {
                    // End of the opening sequence: start track 1 (don't advance).
                    startupFirst = null
                    segueRunning = false
                    playSingleBroadcast(opening)
                    onBroadcastTrackStarted()
                    advancing = false
                } else {
                    advanceBroadcast()
                }
            }
            is SegueStep.Say -> {
                val player = djPlayer ?: run { runNextSegueStep(); return }
                djSpeaking = true
                pushSnapshot()
                scope.launch(Dispatchers.Default) {
                    val ok = player.preload(step.text, announcerSpeed)
                    withContext(mainDispatcher) {
                        Log.d(TAG, "broadcast: segue say (ok=$ok) — \"${step.text}\"")
                        if (ok) {
                            player.playPreloaded(mixer.state.value.masterGain * announcerVolume) {
                                djSpeaking = false
                                runNextSegueStep()
                            }
                        } else {
                            djSpeaking = false
                            runNextSegueStep()
                        }
                    }
                }
            }
            is SegueStep.Jingle -> {
                val uri = step.uri ?: nextJingleUri()
                if (uri == null) { runNextSegueStep(); return }
                Log.d(TAG, "broadcast: segue jingle — $uri")
                playingJingle = true
                pushSnapshot()
                playJingleItem(uri)
                // STATE_ENDED (or onPlayerError) for the jingle continues the segue.
            }
        }
    }

    private fun playJingleItem(uri: String) {
        val c = controller ?: run { playingJingle = false; runNextSegueStep(); return }
        applyItemGain(uri)
        c.setPlaybackParameters(PlaybackParameters(1f))
        c.setMediaItem(
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Station ident")
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
        mixer.setItemGain(1f) // Phase 12: only the broadcast rotation is levelled
        djSpeaking = false
        advancing = false
        broadcastGen++
        seguePlan.clear()
        playingJingle = false
        segueRunning = false
        announceEveryTrack = false
        announcerVolume = 1f
        announcerSpeed = 1f
        jingleUris = emptyList()
        jingleEvery = 0
        tracksSinceJingle = 0
        jingleQueue.clear()
        lastJingleUri = null
        startupJingleUri = null
        startupFirst = null
        broadcastErrorStreak = 0
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
            isPodcast = isPodcast,
            podcastFeedId = podcastFeedId,
            podcastEpisodeGuid = podcastEpisodeGuid,
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
            mediaUri = if (isRadio || isPodcast) {
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
        /** Broadcast: bail out after this many unplayable tracks back to back. */
        const val MAX_BROADCAST_ERROR_STREAK = 6
        /** Broadcast: only a jingle shorter than this opens the show. */
        const val STARTUP_JINGLE_MAX_MS = 45_000L
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
