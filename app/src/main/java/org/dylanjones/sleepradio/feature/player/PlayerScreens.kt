package org.dylanjones.sleepradio.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dylanjones.sleepradio.core.audio.BinauralPreset
import org.dylanjones.sleepradio.core.audio.NoiseColor
import org.dylanjones.sleepradio.core.data.PRESET_COUNT
import org.dylanjones.sleepradio.core.data.SourceSlot
import org.dylanjones.sleepradio.core.data.SourceType
import org.dylanjones.sleepradio.core.design.LocalAppSkin
import org.dylanjones.sleepradio.core.design.RotaryKnob
import org.dylanjones.sleepradio.core.design.SkinPanel
import org.dylanjones.sleepradio.core.design.SkinTile
import org.dylanjones.sleepradio.core.design.TransportCluster
import org.dylanjones.sleepradio.core.design.Wordmark

/** UI callbacks for the player screen. */
class PlayerActions(
    val onMenu: () -> Unit,
    val onPresetClick: (Int) -> Unit,
    val onPresetLongClick: (Int) -> Unit,
    val onPlayPause: () -> Unit,
    /** Next track / +1 min while an audiobook plays. */
    val onNext: () -> Unit,
    /** Previous track / −1 min while an audiobook plays. */
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onVolumeChange: (Float) -> Unit,
    val onBalanceChange: (Float) -> Unit,
    /** SLEEP tile tap — start the timer, or cancel a running one. */
    val onSleepTap: () -> Unit,
    /** SLEEP tile long-press — open the duration picker. */
    val onSleepDurationPick: () -> Unit,
    /** NOISE tile tap — toggle Channel B on/off. */
    val onNoiseToggle: () -> Unit,
    /** NOISE tile long-press — open the spectrum picker. */
    val onNoiseColorPick: () -> Unit,
)

private val placeholderFreqs = listOf("94.7", "101.3", "88.5", "106.1")

/**
 * The single player layout. Both skins render this — the skin only changes the
 * visual treatment (colours, panel texture, now-playing visual), never which
 * controls are shown. See RETROSYNC_PLAN.md section 3.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    val pb = state.playback
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlayerHeader(onMenu = actions.onMenu) { Wordmark() }
        Spacer(Modifier.height(6.dp))
        NowPlayingBlock(state = pb, onSeek = actions.onSeek)
        Spacer(Modifier.height(10.dp))

        SkinPanel(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(PRESET_COUNT) { i ->
                    val slot = state.presets.getOrNull(i)
                    PresetTile(
                        index = i,
                        slot = slot,
                        active = slot != null && slot.refId == state.nowPlayingRef,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { actions.onPresetClick(i) },
                        onLongClick = { actions.onPresetLongClick(i) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkinTile(
                    onClick = actions.onSleepTap,
                    onLongClick = actions.onSleepDurationPick,
                    active = state.sleepActive,
                    contentDescription = if (state.sleepActive) {
                        "Sleep timer running, ${formatTime(state.sleepRemainingMs)} left. " +
                            "Double tap to cancel."
                    } else {
                        "Sleep timer, ${state.sleepDurationMin} minutes. Double tap to start, " +
                            "long press to change."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    TileTitle("SLEEP")
                    Text("🌙", fontSize = 20.sp)
                    TileSub(
                        if (state.sleepActive) formatTime(state.sleepRemainingMs)
                        else "${state.sleepDurationMin} MIN",
                    )
                }
                val binauralOn = state.binaural != BinauralPreset.OFF
                SkinTile(
                    onClick = actions.onNoiseToggle,
                    onLongClick = actions.onNoiseColorPick,
                    active = state.noiseEnabled || binauralOn,
                    contentDescription = "Noise and binaural: " +
                        ambientSummary(state.noiseEnabled, state.noiseColor, binauralOn) +
                        ". Double tap to toggle noise, long press for the ambient mix.",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    TileTitle("NOISE")
                    Text("〜", fontSize = 20.sp)
                    TileSub(ambientSummary(state.noiseEnabled, state.noiseColor, binauralOn))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // For an audiobook, prev/next become −1 min / +1 min and stay enabled;
        // chapter position is shown in the now-playing block up top.
        TransportCluster(
            isPlaying = pb.isPlaying,
            hasPrevious = pb.isAudiobook || pb.hasPrevious,
            hasNext = pb.isAudiobook || pb.hasNext,
            onPrevious = actions.onPrevious,
            onPlayPause = actions.onPlayPause,
            onNext = actions.onNext,
            isAudiobook = pb.isAudiobook,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RotaryKnob("VOL", state.volume, actions.onVolumeChange)
            RotaryKnob("BAL", state.balance, actions.onBalanceChange)
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun SourceType.badge(): String = when (this) {
    SourceType.ALBUM -> "ALBUM"
    SourceType.MUSIC_FOLDER -> "ALBUM"
    SourceType.AUDIOBOOK -> "BOOK"
    SourceType.RADIO -> "RADIO"
}

internal fun NoiseColor.label(): String = when (this) {
    NoiseColor.WHITE -> "WHITE"
    NoiseColor.PINK -> "PINK"
    NoiseColor.BROWN -> "BROWN"
    NoiseColor.BLUE -> "BLUE"
    NoiseColor.DEEP_SPACE -> "DEEP SPACE"
    NoiseColor.AMBIENT -> "AMBIENT"
}

/** NOISE-tile sub-label covering both ambient channels (long-press = config). */
private fun ambientSummary(noiseOn: Boolean, color: NoiseColor, binauralOn: Boolean): String = when {
    noiseOn && binauralOn -> "${color.label()} +β"
    noiseOn -> color.label()
    binauralOn -> "BEATS"
    else -> "OFF"
}

@Composable
private fun PresetTile(
    index: Int,
    slot: SourceSlot?,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val onTile = LocalAppSkin.current.colors.onTile
    val label = if (slot == null) {
        "Preset ${index + 1}, empty. Double tap to assign a source."
    } else {
        "Preset ${index + 1}, ${slot.label}${if (active) ", playing" else ""}. " +
            "Double tap to play, long press to clear."
    }
    SkinTile(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        active = active,
        contentDescription = label,
    ) {
        Text(
            text = "${index + 1}",
            color = onTile,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        if (slot == null) {
            Text("[FM]", color = onTile, fontSize = 10.sp)
            Text(
                text = placeholderFreqs.getOrElse(index) { "—" },
                color = onTile,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            Text(
                slot.type.badge(),
                color = onTile.copy(alpha = 0.6f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                slot.label,
                color = onTile,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
            )
        }
    }
}

@Composable
private fun TileTitle(text: String) {
    Text(
        text = text,
        color = LocalAppSkin.current.colors.onTile,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun TileSub(text: String) {
    Text(
        text = text,
        color = LocalAppSkin.current.colors.onTile,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        lineHeight = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
