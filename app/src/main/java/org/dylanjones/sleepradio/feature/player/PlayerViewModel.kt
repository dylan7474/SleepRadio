package org.dylanjones.sleepradio.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dylanjones.sleepradio.core.audio.AmbientPattern
import org.dylanjones.sleepradio.core.audio.BinauralPreset
import org.dylanjones.sleepradio.core.audio.MixerController
import org.dylanjones.sleepradio.core.audio.NoiseColor
import org.dylanjones.sleepradio.core.broadcast.BroadcastConfig
import org.dylanjones.sleepradio.core.broadcast.BroadcastTrack
import org.dylanjones.sleepradio.core.data.AMBIENT_PATTERN_SLOTS
import org.dylanjones.sleepradio.core.data.Audiobook
import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_OFF
import org.dylanjones.sleepradio.core.data.FolderAlbum
import org.dylanjones.sleepradio.core.data.PRESET_COUNT
import org.dylanjones.sleepradio.core.data.RadioDirectory
import org.dylanjones.sleepradio.core.data.RadioStation
import org.dylanjones.sleepradio.core.data.SettingsRepository
import org.dylanjones.sleepradio.core.data.SlotRepository
import org.dylanjones.sleepradio.core.data.SourceSlot
import org.dylanjones.sleepradio.core.data.SourceType
import org.dylanjones.sleepradio.core.data.db.AudiobookProgressDao
import org.dylanjones.sleepradio.core.data.db.AudiobookProgressEntity
import org.dylanjones.sleepradio.core.data.db.toDomain
import org.dylanjones.sleepradio.core.tts.VoicePackResolver
import org.dylanjones.sleepradio.media.AudiobookRepository
import org.dylanjones.sleepradio.media.MusicRepository
import org.dylanjones.sleepradio.playback.PlaybackConnection
import org.dylanjones.sleepradio.playback.PlaybackState
import org.dylanjones.sleepradio.playback.SleepTimerState
import javax.inject.Inject

data class PlayerUiState(
    /** Music-folder albums from the user-chosen SAF tree (the only music source). */
    val folderAlbums: List<FolderAlbum> = emptyList(),
    val musicFolderChosen: Boolean = false,
    /** Bundled + user-added radio stations. */
    val stations: List<RadioStation> = emptyList(),
    /** Which of [stations] are user-added (deletable). */
    val customStationIds: Set<String> = emptySet(),
    /** Results of the last online-directory search. */
    val directoryResults: List<RadioStation> = emptyList(),
    val directorySearching: Boolean = false,
    val audiobooks: List<Audiobook> = emptyList(),
    val audiobooksFolderChosen: Boolean = false,
    val playback: PlaybackState = PlaybackState(),
    val nowPlayingRef: String? = null,
    val presets: List<SourceSlot?> = List(PRESET_COUNT) { null },
    val pickerForSlot: Int? = null,
    /** VOL knob 0..1 — master gain, applied to Channel A output. */
    val volume: Float = 0.8f,
    /** BAL knob 0..1 (0 = main, 1 = noise) — equal-power A↔B crossfade. */
    val balance: Float = 0.5f,
    /** Configured sleep-timer duration in minutes. */
    val sleepDurationMin: Int = 30,
    /** Sleep timer currently running (Channel A will fade + stop). */
    val sleepActive: Boolean = false,
    /** Milliseconds left on the running sleep timer. */
    val sleepRemainingMs: Long = 0L,
    /** Channel B (noise) on/off. */
    val noiseEnabled: Boolean = false,
    /** Channel B noise spectrum. */
    val noiseColor: NoiseColor = NoiseColor.WHITE,
    /** Channel C (binaural) preset — OFF = disabled. */
    val binaural: BinauralPreset = BinauralPreset.OFF,
    /** Channel C level 0..1 (settings-controlled, outside BAL). */
    val binauralLevel: Float = 0.4f,
    /** Saved ambient PATTERN slots (null = empty). */
    val patterns: List<AmbientPattern?> = List(AMBIENT_PATTERN_SLOTS) { null },
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val musicRepository: MusicRepository,
    private val audiobookRepository: AudiobookRepository,
    private val playback: PlaybackConnection,
    private val mixer: MixerController,
    private val slots: SlotRepository,
    private val settings: SettingsRepository,
    private val progressDao: AudiobookProgressDao,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<PlayerUiState> =
        combine(
            local,
            playback.state,
            mixer.state,
            mixer.ambient,
            slots.slots,
        ) { l, pb, mx, amb, presetSlots ->
            PlayerUiState(
                folderAlbums = l.folderAlbums,
                musicFolderChosen = l.musicTreeUri != null,
                stations = RadioStation.bundled + l.customStations,
                customStationIds = l.customStations.mapTo(HashSet()) { it.id },
                directoryResults = l.directoryResults,
                directorySearching = l.directorySearching,
                audiobooks = l.audiobooks,
                audiobooksFolderChosen = l.audiobooksTreeUri != null,
                playback = pb,
                nowPlayingRef = l.nowPlayingRef,
                presets = presetSlots,
                pickerForSlot = l.pickerForSlot,
                volume = mx.masterGain,
                balance = mx.crossfade,
                sleepDurationMin = l.sleepDurationMin,
                sleepActive = l.sleepTimer.active,
                sleepRemainingMs = l.sleepTimer.remainingMs,
                noiseEnabled = amb.noiseEnabled,
                noiseColor = amb.noiseColor,
                binaural = amb.binaural,
                binauralLevel = mx.binauralLevel,
                patterns = l.ambientPatterns,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    private var lastProgressSaveMs = 0L

    init {
        // Compute the library list first, THEN publish it in a single atomic
        // update. Doing `local.value = local.value.copy(x = suspendingCall())`
        // captures a stale `local.value` receiver across the suspension point,
        // so the audiobook and music collectors racing here would clobber each
        // other's lists.
        settings.audiobooksTreeUri.onEach { uri ->
            val books = uri?.let {
                runCatching { audiobookRepository.listBooks(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            local.update { it.copy(audiobooksTreeUri = uri, audiobooks = books) }
        }.launchIn(viewModelScope)

        settings.musicTreeUri.onEach { uri ->
            val albums = uri?.let {
                runCatching { musicRepository.folderAlbums(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            local.update { it.copy(musicTreeUri = uri, folderAlbums = albums) }
        }.launchIn(viewModelScope)

        // Restore the last ambient mix, then persist every change. Seeding and
        // the persist collector share one coroutine so seed always wins the race
        // (a persist write must never land before restore).
        viewModelScope.launch {
            settings.ambient.first()?.let { mixer.applyPattern(it) }
            combine(mixer.ambient, mixer.state) { _, _ -> mixer.currentPattern() }
                .distinctUntilChanged()
                .drop(1)
                .collect { settings.setAmbient(it) }
        }
        settings.ambientPatterns
            .onEach { list -> local.update { it.copy(ambientPatterns = list) } }
            .launchIn(viewModelScope)

        // Sleep-timer: seed the duration, mirror the running timer into UI state.
        settings.sleepDurationMin
            .onEach { min -> local.update { it.copy(sleepDurationMin = min) } }
            .launchIn(viewModelScope)
        settings.customStations
            .onEach { list -> local.update { it.copy(customStations = list) } }
            .launchIn(viewModelScope)
        playback.sleepTimer
            .onEach { st -> local.update { it.copy(sleepTimer = st) } }
            .launchIn(viewModelScope)

        // Persist audiobook progress every 5 s while playing, and once more on
        // the play→pause edge (so the sleep timer / a manual pause don't leave
        // the resume point up to 5 s stale).
        playback.state.onEach { pb ->
            if (pb.isAudiobook && pb.bookId != null) {
                val now = System.currentTimeMillis()
                val pausedEdge = wasPlaying && !pb.isPlaying
                if ((pb.isPlaying && now - lastProgressSaveMs > 5_000) || pausedEdge) {
                    lastProgressSaveMs = now
                    progressDao.upsert(
                        AudiobookProgressEntity(
                            bookId = pb.bookId!!,
                            chapterIndex = pb.chapterIndex,
                            positionMs = pb.positionMs,
                            updatedAt = now,
                        ),
                    )
                }
            }
            wasPlaying = pb.isPlaying
        }.launchIn(viewModelScope)
    }

    private var wasPlaying = false

    fun onAudiobooksFolderChosen(treeUri: String) {
        viewModelScope.launch { settings.setAudiobooksTreeUri(treeUri) }
    }

    fun onMusicFolderChosen(treeUri: String) {
        viewModelScope.launch { settings.setMusicTreeUri(treeUri) }
    }

    /** Tap a preset slot: play it if assigned, otherwise open the picker for it. */
    fun onPresetClicked(index: Int) {
        val slot = uiState.value.presets.getOrNull(index)
        if (slot == null) {
            local.value = local.value.copy(pickerForSlot = index)
        } else {
            playSlot(slot)
        }
    }

    fun dismissPicker() {
        local.value = local.value.copy(pickerForSlot = null)
    }

    fun assignStationToSlot(index: Int, station: RadioStation) {
        val slot = SourceSlot(
            index = index,
            type = SourceType.RADIO,
            refId = station.streamUrl,
            label = station.name,
            sublabel = station.description,
        )
        viewModelScope.launch { slots.assign(slot) }
        local.value = local.value.copy(pickerForSlot = null)
        playSlot(slot)
    }

    // --- custom radio stations & online directory ---

    /** Add a station from the manual "name + URL" form. */
    fun addManualStation(name: String, url: String) {
        val n = name.trim()
        var u = url.trim()
        if (u.isBlank()) return
        if (!u.contains("://")) u = "http://$u"
        val station = RadioStation(
            id = "custom_${u.hashCode().toUInt().toString(16)}",
            name = n.ifBlank { u.substringAfter("://").substringBefore("/") },
            streamUrl = u,
            description = "Added by you",
        )
        viewModelScope.launch { settings.addCustomStation(station) }
    }

    fun removeStation(id: String) {
        viewModelScope.launch { settings.removeCustomStation(id) }
    }

    fun searchDirectory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            local.update { it.copy(directoryResults = emptyList(), directorySearching = false) }
            return
        }
        local.update { it.copy(directorySearching = true) }
        viewModelScope.launch {
            val results = RadioDirectory.search(q)
            local.update { it.copy(directoryResults = results, directorySearching = false) }
        }
    }

    fun clearDirectory() {
        local.update { it.copy(directoryResults = emptyList(), directorySearching = false) }
    }

    /** Directory result tapped: remember it, assign to [index], and play. */
    fun addAndAssignStation(index: Int, station: RadioStation) {
        viewModelScope.launch { settings.addCustomStation(station) }
        assignStationToSlot(index, station)
    }

    /** Persist a station (e.g. a directory result) without assigning it to a slot. */
    fun saveCustomStation(station: RadioStation) {
        viewModelScope.launch { settings.addCustomStation(station) }
    }

    /** Play a station on Channel A immediately, without occupying a preset slot. */
    fun playStationNow(station: RadioStation) {
        playback.playRadio(station.streamUrl, station.name, station.description)
        local.update { it.copy(nowPlayingRef = station.streamUrl) }
    }

    fun assignFolderAlbumToSlot(index: Int, album: FolderAlbum) {
        val slot = SourceSlot(
            index = index,
            type = SourceType.MUSIC_FOLDER,
            refId = album.id,
            label = album.title,
            sublabel = album.artist.ifBlank { "${album.trackCount} tracks" },
        )
        viewModelScope.launch { slots.assign(slot) }
        local.value = local.value.copy(pickerForSlot = null)
        playSlot(slot)
    }

    fun assignAudiobookToSlot(index: Int, book: Audiobook) {
        val slot = SourceSlot(
            index = index,
            type = SourceType.AUDIOBOOK,
            refId = book.id,
            label = book.title,
            sublabel = "${book.chapterCount} chapter${if (book.chapterCount == 1) "" else "s"}",
        )
        viewModelScope.launch { slots.assign(slot) }
        local.value = local.value.copy(pickerForSlot = null)
        playSlot(slot)
    }

    fun assignBroadcastToSlot(index: Int) {
        val slot = SourceSlot(
            index = index,
            type = SourceType.BROADCAST,
            refId = "broadcast",
            label = "SleepRadio broadcast",
            sublabel = "Auto-DJ · your music folder",
        )
        viewModelScope.launch { slots.assign(slot) }
        local.value = local.value.copy(pickerForSlot = null)
        playSlot(slot)
    }

    fun clearSlot(index: Int) {
        viewModelScope.launch { slots.clear(index) }
    }

    private fun playSlot(slot: SourceSlot) {
        when (slot.type) {
            // Legacy slots assigned before music became folder-only still play
            // via MediaStore by album id.
            SourceType.ALBUM -> viewModelScope.launch {
                val id = slot.refId.toLongOrNull() ?: return@launch
                val tracks = musicRepository.tracksForAlbum(id)
                if (tracks.isEmpty()) return@launch
                playback.playTracks(tracks)
                local.value = local.value.copy(nowPlayingRef = slot.refId)
            }

            SourceType.MUSIC_FOLDER -> viewModelScope.launch {
                val tree = local.value.musicTreeUri ?: return@launch
                val files = musicRepository.folderTracks(tree, slot.refId)
                if (files.isEmpty()) return@launch
                playback.playFolderAlbum(files, slot.label)
                local.value = local.value.copy(nowPlayingRef = slot.refId)
            }

            SourceType.RADIO -> {
                playback.playRadio(slot.refId, slot.label, slot.sublabel)
                local.value = local.value.copy(nowPlayingRef = slot.refId)
            }

            SourceType.AUDIOBOOK -> viewModelScope.launch {
                val tree = local.value.audiobooksTreeUri ?: return@launch
                val chapters = audiobookRepository.chapters(tree, slot.refId)
                if (chapters.isEmpty()) return@launch
                val progress = progressDao.get(slot.refId)?.toDomain()
                playback.playAudiobook(
                    book = slot.refId,
                    chapters = chapters,
                    bookTitle = slot.label,
                    startChapter = progress?.chapterIndex ?: 0,
                    startPositionMs = progress?.positionMs ?: 0L,
                )
                local.value = local.value.copy(nowPlayingRef = slot.refId)
            }

            SourceType.BROADCAST -> viewModelScope.launch {
                val tree = local.value.musicTreeUri ?: return@launch
                val pool = musicRepository.folderAlbums(tree).flatMap { album ->
                    musicRepository.folderTracks(tree, album.id).map { ch ->
                        BroadcastTrack(
                            uri = ch.uri,
                            title = cleanTrackTitle(ch.title),
                            artist = album.artist.ifBlank { album.title },
                            album = album.title,
                        )
                    }
                }
                if (pool.isEmpty()) return@launch
                val voiceId = settings.broadcastVoice.first()
                val pack = if (voiceId == BROADCAST_VOICE_OFF) {
                    null
                } else {
                    VoicePackResolver(appContext).byId(voiceId)
                }
                playback.startBroadcast(pool, pack, BroadcastConfig())
                local.value = local.value.copy(nowPlayingRef = slot.refId)
            }
        }
    }

    /** Strip a leading track number ("01 - ", "1. ", "007_") from a filename title. */
    private fun cleanTrackTitle(raw: String): String =
        raw.replace(Regex("^\\s*\\d{1,3}\\s*[-._)]+\\s*"), "").trim().ifEmpty { raw }

    fun playPause() = playback.playPause()

    /** Audiobook: jump +1 min. Otherwise: next track in the queue. */
    fun next() =
        if (uiState.value.playback.isAudiobook) playback.skipBy(60_000L) else playback.next()

    /** Audiobook: jump −1 min. Otherwise: previous track in the queue. */
    fun previous() =
        if (uiState.value.playback.isAudiobook) playback.skipBy(-60_000L) else playback.previous()

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun onVolumeChange(value: Float) = mixer.setVolume(value)
    fun onBalanceChange(value: Float) = mixer.setBalance(value)
    fun toggleNoise() = mixer.toggleNoise()
    fun setNoiseColor(color: NoiseColor) = mixer.setNoiseColor(color)
    fun setBinaural(preset: BinauralPreset) = mixer.setBinaural(preset)
    fun setBinauralLevel(value: Float) = mixer.setBinauralLevel(value)

    /** Save the current ambient mix into PATTERN slot [index]. */
    fun savePattern(index: Int) {
        viewModelScope.launch { settings.setAmbientPattern(index, mixer.currentPattern()) }
    }

    /** Recall PATTERN slot [index] into the live mix (no-op if empty). */
    fun recallPattern(index: Int) {
        uiState.value.patterns.getOrNull(index)?.let { mixer.applyPattern(it) }
    }

    fun clearPattern(index: Int) {
        viewModelScope.launch { settings.setAmbientPattern(index, null) }
    }

    /** SLEEP tile tap: start the timer at the configured duration, or cancel it. */
    fun onSleepTap() {
        if (uiState.value.sleepActive) {
            playback.cancelSleepTimer()
        } else {
            playback.startSleepTimer(uiState.value.sleepDurationMin * 60_000L)
        }
    }

    fun setSleepDuration(minutes: Int) {
        local.update { it.copy(sleepDurationMin = minutes) }
        viewModelScope.launch { settings.setSleepDurationMin(minutes) }
        if (uiState.value.sleepActive) playback.startSleepTimer(minutes * 60_000L)
    }

    private data class LocalState(
        val musicTreeUri: String? = null,
        val folderAlbums: List<FolderAlbum> = emptyList(),
        val audiobooksTreeUri: String? = null,
        val audiobooks: List<Audiobook> = emptyList(),
        val nowPlayingRef: String? = null,
        val pickerForSlot: Int? = null,
        val sleepDurationMin: Int = 30,
        val sleepTimer: SleepTimerState = SleepTimerState(),
        val ambientPatterns: List<AmbientPattern?> = List(AMBIENT_PATTERN_SLOTS) { null },
        val customStations: List<RadioStation> = emptyList(),
        val directoryResults: List<RadioStation> = emptyList(),
        val directorySearching: Boolean = false,
    )
}
