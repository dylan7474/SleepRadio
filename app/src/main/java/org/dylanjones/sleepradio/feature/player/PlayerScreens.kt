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
import org.dylanjones.sleepradio.core.design.LocalAppSkin
import org.dylanjones.sleepradio.core.design.RotaryKnob
import org.dylanjones.sleepradio.core.design.SkinPanel
import org.dylanjones.sleepradio.core.design.SkinTile
import org.dylanjones.sleepradio.core.design.TransportCluster
import org.dylanjones.sleepradio.core.design.Wordmark

/** UI callbacks for the player screen. */
class PlayerActions(
    val onMenu: () -> Unit,
    val onBell: () -> Unit,
    val onPresetClick: (Int) -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onVolumeChange: (Float) -> Unit,
    val onBalanceChange: (Float) -> Unit,
    val onSleepFractionChange: (Float) -> Unit,
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
        PlayerHeader(onMenu = actions.onMenu, onBell = actions.onBell) { Wordmark() }
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
                    PresetTile(
                        index = i,
                        slot = state.presets.getOrNull(i),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { actions.onPresetClick(i) },
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
                    onClick = {},
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    TileTitle("SLEEP")
                    Text("🌙", fontSize = 20.sp)
                    TileSub("${state.sleepMinutes} MIN")
                }
                SkinTile(
                    onClick = {},
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    TileTitle("NOISE")
                    Text("〜", fontSize = 20.sp)
                    TileSub("AMBIENT")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TransportCluster(
            isPlaying = pb.isPlaying,
            hasPrevious = pb.hasPrevious,
            hasNext = pb.hasNext,
            onPrevious = actions.onPrevious,
            onPlayPause = actions.onPlayPause,
            onNext = actions.onNext,
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

@Composable
private fun PresetTile(
    index: Int,
    slot: PresetSlot?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val onTile = LocalAppSkin.current.colors.onTile
    SkinTile(onClick = onClick, modifier = modifier, active = slot != null) {
        Text(
            text = "${index + 1}",
            color = onTile,
            fontSize = 22.sp,
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
                slot.label,
                color = onTile,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                slot.sublabel,
                color = onTile.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
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
