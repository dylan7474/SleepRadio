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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.dylanjones.sleepradio.core.audio.MixerController
import org.dylanjones.sleepradio.core.design.sleepMinutesFor
import org.dylanjones.sleepradio.media.Album
import org.dylanjones.sleepradio.media.MusicRepository
import org.dylanjones.sleepradio.playback.PlaybackConnection
import org.dylanjones.sleepradio.playback.PlaybackState
import javax.inject.Inject

/** An assigned source-preset slot. Room-backed with the full source model in Phase 4. */
data class PresetSlot(
    val albumId: Long,
    val label: String,
    val sublabel: String,
)

data class PlayerUiState(
    val hasAudioPermission: Boolean = false,
    val isLoadingLibrary: Boolean = false,
    val albums: List<Album> = emptyList(),
    val playback: PlaybackState = PlaybackState(),
    val nowPlayingAlbumId: Long? = null,
    val presets: List<PresetSlot?> = List(PRESET_COUNT) { null },
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

const val PRESET_COUNT = 4

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val playback: PlaybackConnection,
    private val mixer: MixerController,
) : ViewModel() {

    private val local = MutableStateFlow(
        LocalState(hasAudioPermission = readPermission()),
    )

    val uiState: StateFlow<PlayerUiState> =
        combine(local, playback.state, mixer.state) { l, pb, mx ->
            PlayerUiState(
                hasAudioPermission = l.hasAudioPermission,
                isLoadingLibrary = l.isLoadingLibrary,
                albums = l.albums,
                playback = pb,
                nowPlayingAlbumId = l.nowPlayingAlbumId,
                presets = l.presets,
                pickerForSlot = l.pickerForSlot,
                volume = mx.masterGain,
                balance = mx.crossfade,
                sleepFraction = l.sleepFraction,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    init {
        if (local.value.hasAudioPermission) loadLibrary()
    }

    fun onAudioPermissionResult(granted: Boolean) {
        local.value = local.value.copy(hasAudioPermission = granted)
        if (granted && local.value.albums.isEmpty()) loadLibrary()
    }

    fun refreshLibrary() = loadLibrary()

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            val tracks = musicRepository.tracksForAlbum(album.id)
            if (tracks.isEmpty()) return@launch
            playback.playTracks(tracks)
            local.value = local.value.copy(nowPlayingAlbumId = album.id)
        }
    }

    /** Tap a preset slot: play it if assigned, otherwise open the album picker for it. */
    fun onPresetClicked(index: Int) {
        val slot = local.value.presets.getOrNull(index)
        if (slot == null) {
            local.value = local.value.copy(pickerForSlot = index)
        } else {
            local.value.albums.firstOrNull { it.id == slot.albumId }?.let(::playAlbum)
        }
    }

    fun dismissPicker() {
        local.value = local.value.copy(pickerForSlot = null)
    }

    fun assignPresetAndPlay(index: Int, album: Album) {
        val updated = local.value.presets.toMutableList().also {
            it[index] = PresetSlot(
                albumId = album.id,
                label = album.title,
                sublabel = album.artist,
            )
        }
        local.value = local.value.copy(presets = updated, pickerForSlot = null)
        playAlbum(album)
    }

    fun playPause() = playback.playPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun onVolumeChange(value: Float) = mixer.setVolume(value)
    fun onBalanceChange(value: Float) = mixer.setBalance(value)
    fun onSleepFractionChange(value: Float) {
        local.value = local.value.copy(sleepFraction = value.coerceIn(0f, 1f))
    }

    private fun loadLibrary() {
        if (local.value.isLoadingLibrary) return
        local.value = local.value.copy(isLoadingLibrary = true)
        viewModelScope.launch {
            val albums = musicRepository.albums()
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
        val nowPlayingAlbumId: Long? = null,
        val presets: List<PresetSlot?> = List(PRESET_COUNT) { null },
        val pickerForSlot: Int? = null,
        val sleepFraction: Float = 1f / 3f,
    )
}
