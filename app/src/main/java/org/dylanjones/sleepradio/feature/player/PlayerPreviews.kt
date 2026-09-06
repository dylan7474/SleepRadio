package org.dylanjones.sleepradio.feature.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.dylanjones.sleepradio.core.design.IndustrialSkin
import org.dylanjones.sleepradio.core.design.NeonSkin
import org.dylanjones.sleepradio.core.design.SkinBackground
import org.dylanjones.sleepradio.playback.PlaybackState

private val previewState = PlayerUiState(
    hasAudioPermission = true,
    playback = PlaybackState(
        isConnected = true,
        isPlaying = true,
        title = "Neon Horizons",
        artist = "Cyberwave City",
        positionMs = 165_000,
        durationMs = 252_000,
        hasNext = true,
        hasPrevious = false,
        queueSize = 12,
    ),
    volume = 0.7f,
    balance = 0.4f,
    presets = listOf(
        PresetSlot(1, "Abbey Road", "The Beatles"),
        null,
        PresetSlot(3, "By The Way", "Red Hot Chili Peppers"),
        null,
    ),
)

private val noopActions = PlayerActions(
    onMenu = {},
    onBell = {},
    onPresetClick = {},
    onPlayPause = {},
    onNext = {},
    onPrevious = {},
    onSeek = {},
    onVolumeChange = {},
    onBalanceChange = {},
    onSleepFractionChange = {},
)

@Preview(name = "Neon", widthDp = 412, heightDp = 900)
@Composable
private fun NeonPlayerPreview() {
    SkinBackground(NeonSkin) {
        PlayerScreen(previewState, noopActions, Modifier.fillMaxSize())
    }
}

@Preview(name = "Industrial", widthDp = 412, heightDp = 900)
@Composable
private fun IndustrialPlayerPreview() {
    SkinBackground(IndustrialSkin) {
        PlayerScreen(previewState, noopActions, Modifier.fillMaxSize())
    }
}
