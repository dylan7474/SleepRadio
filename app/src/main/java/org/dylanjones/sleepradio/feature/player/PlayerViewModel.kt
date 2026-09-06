package org.dylanjones.sleepradio.feature.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.dylanjones.sleepradio.core.audio.MixerController
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
import org.dylanjones.sleepradio.media.Album
import org.dylanjones.sleepradio.media.AudiobookRepository
import org.dylanjones.sleepradio.media.MusicRepository
import org.dylanjones.sleepradio.playback.PlaybackConnection
import org.dylanjones.sleepradio.playback.PlaybackState
import javax.inject.Inject

private val SPEED_CYCLE = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 0.85f)

data class PlayerUiState(
    val hasAudioPermission: Boolean = false,
    val isLoadingLibrary: Boolean = false,
    val albums: List<Album> = emptyList(),
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
) {
    val sleepMinutes: Int get() = sleepMinutesFor(sleepFraction)
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val audiobookRepository: AudiobookRepository,
    private val playback: PlaybackConnection,
    private val mixer: MixerController,
    private val slots: SlotRepository,
    private val settings: SettingsRepository,
    private val progressDao: AudiobookProgressDao,
) : ViewModel() {

    private val local = MutableStateFlow(
        LocalState(hasAudioPermission = readPermission()),
    )

    val uiState: StateFlow<PlayerUiState> =
        combine(local, playback.state, mixer.state, slots.slots) { l, pb, mx, presetSlots ->
            PlayerUiState(
                hasAudioPermission = l.hasAudioPermission,
                isLoadingLibrary = l.isLoadingLibrary,
                albums = l.albums,
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
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    private var lastProgressSaveMs = 0L

    init {
        loadLibrary()

        settings.audiobooksTreeUri.onEach { uri ->
            local.value = local.value.copy(audiobooksTreeUri = uri)
            local.value = local.value.copy(
                audiobooks = uri?.let { runCatching { audiobookRepository.listBooks(it) }.getOrDefault(emptyList()) }
                    ?: emptyList(),
            )
        }.launchIn(viewModelScope)

        settings.musicTreeUri.onEach { uri ->
            local.value = local.value.copy(musicTreeUri = uri)
            local.value = local.value.copy(
                folderAlbums = uri?.let {
                    runCatching { musicRepository.folderAlbums(it) }.getOrDefault(emptyList())
                } ?: emptyList(),
            )
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

    fun onAudioPermissionResult(granted: Boolean) {
        local.value = local.value.copy(hasAudioPermission = granted)
        if (granted && local.value.albums.isEmpty()) loadLibrary()
    }

    fun refreshLibrary() = loadLibrary()

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
            // Re-check permission and (re)load the library so the picker is current.
            local.value = local.value.copy(
                hasAudioPermission = readPermission(),
                pickerForSlot = index,
            )
            loadLibrary(force = true)
        } else {
            playSlot(slot)
        }
    }

    fun retryLibraryLoad() {
        local.value = local.value.copy(hasAudioPermission = readPermission())
        loadLibrary(force = true)
    }

    fun dismissPicker() {
        local.value = local.value.copy(pickerForSlot = null)
    }

    fun assignAlbumToSlot(index: Int, album: Album) {
        val slot = SourceSlot(
            index = index,
            type = SourceType.ALBUM,
            refId = album.id.toString(),
            label = album.title,
            sublabel = album.artist,
            artworkUri = album.artworkUri?.toString(),
        )
        viewModelScope.launch { slots.assign(slot) }
        local.value = local.value.copy(pickerForSlot = null)
        playSlot(slot)
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
                    speed = 1f,
                )
                local.value = local.value.copy(nowPlayingRef = slot.refId)
            }
        }
    }

    fun playPause() = playback.playPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun skipBack() = playback.skipBy(-30_000L)
    fun skipForward() = playback.skipBy(30_000L)

    fun cycleSpeed() {
        val current = uiState.value.playback.speed
        val next = SPEED_CYCLE.firstOrNull { it > current + 0.01f } ?: SPEED_CYCLE.first()
        playback.setSpeed(next)
    }

    fun onVolumeChange(value: Float) = mixer.setVolume(value)
    fun onBalanceChange(value: Float) = mixer.setBalance(value)
    fun onSleepFractionChange(value: Float) {
        local.value = local.value.copy(sleepFraction = value.coerceIn(0f, 1f))
    }

    private fun loadLibrary(force: Boolean = false) {
        if (local.value.isLoadingLibrary && !force) return
        local.value = local.value.copy(isLoadingLibrary = true)
        viewModelScope.launch {
            val albums = try {
                musicRepository.albums()
            } catch (e: Exception) {
                android.util.Log.w("PlayerViewModel", "loadLibrary failed", e)
                local.value.albums
            }
            local.value = local.value.copy(albums = albums, isLoadingLibrary = false)
        }
    }

    private fun readPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private data class LocalState(
        val hasAudioPermission: Boolean = false,
        val isLoadingLibrary: Boolean = false,
        val albums: List<Album> = emptyList(),
        val musicTreeUri: String? = null,
        val folderAlbums: List<FolderAlbum> = emptyList(),
        val audiobooksTreeUri: String? = null,
        val audiobooks: List<Audiobook> = emptyList(),
        val nowPlayingRef: String? = null,
        val pickerForSlot: Int? = null,
        val sleepFraction: Float = 1f / 3f,
    )
}
