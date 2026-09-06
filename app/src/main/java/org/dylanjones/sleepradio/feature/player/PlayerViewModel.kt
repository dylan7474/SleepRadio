package org.dylanjones.sleepradio.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dylanjones.sleepradio.core.audio.MixerController
import org.dylanjones.sleepradio.core.audio.NoiseColor
import org.dylanjones.sleepradio.core.data.Audiobook
import org.dylanjones.sleepradio.core.data.FolderAlbum
import org.dylanjones.sleepradio.core.data.PRESET_COUNT
import org.dylanjones.sleepradio.core.data.RadioStation
import org.dylanjones.sleepradio.core.data.SettingsRepository
import org.dylanjones.sleepradio.core.data.SlotRepository
import org.dylanjones.sleepradio.core.data.SourceSlot
import org.dylanjones.sleepradio.core.data.SourceType
import org.dylanjones.sleepradio.core.data.db.AudiobookProgressDao
import org.dylanjones.sleepradio.core.data.db.AudiobookProgressEntity
import org.dylanjones.sleepradio.core.data.db.toDomain
import org.dylanjones.sleepradio.core.design.sleepMinutesFor
import org.dylanjones.sleepradio.media.AudiobookRepository
import org.dylanjones.sleepradio.media.MusicRepository
import org.dylanjones.sleepradio.playback.PlaybackConnection
import org.dylanjones.sleepradio.playback.PlaybackState
import javax.inject.Inject

data class PlayerUiState(
    /** Music-folder albums from the user-chosen SAF tree (the only music source). */
    val folderAlbums: List<FolderAlbum> = emptyList(),
    val musicFolderChosen: Boolean = false,
    val stations: List<RadioStation> = emptyList(),
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
    /** Sleep-duration slider 0..1 (15–60 min). Wired to the timer in Phase 6. */
    val sleepFraction: Float = 1f / 3f,
    /** Channel B (noise) on/off. */
    val noiseEnabled: Boolean = false,
    /** Channel B noise spectrum. */
    val noiseColor: NoiseColor = NoiseColor.WHITE,
) {
    val sleepMinutes: Int get() = sleepMinutesFor(sleepFraction)
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
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
                stations = RadioStation.bundled,
                audiobooks = l.audiobooks,
                audiobooksFolderChosen = l.audiobooksTreeUri != null,
                playback = pb,
                nowPlayingRef = l.nowPlayingRef,
                presets = presetSlots,
                pickerForSlot = l.pickerForSlot,
                volume = mx.masterGain,
                balance = mx.crossfade,
                sleepFraction = l.sleepFraction,
                noiseEnabled = amb.noiseEnabled,
                noiseColor = amb.noiseColor,
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

        // Persist audiobook progress while it plays.
        playback.state.onEach { pb ->
            if (pb.isAudiobook && pb.bookId != null && pb.isPlaying) {
                val now = System.currentTimeMillis()
                if (now - lastProgressSaveMs > 5_000) {
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
        }.launchIn(viewModelScope)
    }

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
        }
    }

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
    fun onSleepFractionChange(value: Float) {
        local.value = local.value.copy(sleepFraction = value.coerceIn(0f, 1f))
    }

    private data class LocalState(
        val musicTreeUri: String? = null,
        val folderAlbums: List<FolderAlbum> = emptyList(),
        val audiobooksTreeUri: String? = null,
        val audiobooks: List<Audiobook> = emptyList(),
        val nowPlayingRef: String? = null,
        val pickerForSlot: Int? = null,
        val sleepFraction: Float = 1f / 3f,
    )
}
