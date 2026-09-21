package org.dylanjones.sleepradio.feature.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.dylanjones.sleepradio.core.data.SourceSlot
import org.dylanjones.sleepradio.core.data.SourceType
import org.dylanjones.sleepradio.core.design.SkinBackground
import org.dylanjones.sleepradio.core.design.StudioSkin
import org.dylanjones.sleepradio.playback.PlaybackState

internal val previewState = PlayerUiState(
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
        SourceSlot(0, SourceType.ALBUM, "1", "Abbey Road", "The Beatles"),
        SourceSlot(1, SourceType.RADIO, "http://x", "BBC Radio 4", "Speech & drama"),
        null,
        null,
    ),
)

internal val noopActions = PlayerActions(
    onMenu = {},
    onPresetClick = {},
    onPresetLongClick = {},
    onPlayPause = {},
    onNext = {},
    onPrevious = {},
    onSeek = {},
    onVolumeChange = {},
    onBalanceChange = {},
    onSleepTap = {},
    onSleepDurationPick = {},
    onNoiseToggle = {},
    onNoiseColorPick = {},
)

@Preview(name = "Studio", widthDp = 412, heightDp = 900)
@Composable
private fun StudioPlayerPreview() {
    SkinBackground(StudioSkin) {
        PlayerScreen(previewState, noopActions, Modifier.fillMaxSize())
    }
}

/** Debug-lab helper: the no-op actions with a few overridden. */
internal fun PlayerActions.copy2(
    onSeek: (Long) -> Unit = this.onSeek,
    onPlayPause: () -> Unit = this.onPlayPause,
): PlayerActions = PlayerActions(
    onMenu = onMenu,
    onPresetClick = onPresetClick,
    onPresetLongClick = onPresetLongClick,
    onPlayPause = onPlayPause,
    onNext = onNext,
    onPrevious = onPrevious,
    onSeek = onSeek,
    onVolumeChange = onVolumeChange,
    onBalanceChange = onBalanceChange,
    onSleepTap = onSleepTap,
    onSleepDurationPick = onSleepDurationPick,
    onNoiseToggle = onNoiseToggle,
    onNoiseColorPick = onNoiseColorPick,
)
