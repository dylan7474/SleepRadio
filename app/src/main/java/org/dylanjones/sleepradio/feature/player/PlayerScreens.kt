package org.dylanjones.sleepradio.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dylanjones.sleepradio.core.design.LocalAppSkin
import org.dylanjones.sleepradio.core.design.RotaryKnob
import org.dylanjones.sleepradio.core.design.SkinPanel
import org.dylanjones.sleepradio.core.design.SkinTile
import org.dylanjones.sleepradio.core.design.StaticKnob
import org.dylanjones.sleepradio.core.design.TransportCluster
import org.dylanjones.sleepradio.core.design.VerticalSlider
import org.dylanjones.sleepradio.core.design.Wordmark

/** UI callbacks shared by both skinned screens. */
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

// ---------------------------------------------------------------------------
// Neon skin — mockup 1
// ---------------------------------------------------------------------------

@Composable
fun NeonPlayerScreen(
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
                .weight(1f)
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
    SkinTile(onClick = onClick, modifier = modifier, active = slot != null) {
        Text(
            text = "${index + 1}",
            color = LocalAppSkin.current.colors.onTile,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        if (slot == null) {
            Text("[FM]", color = LocalAppSkin.current.colors.onTile, fontSize = 10.sp)
            Text(
                text = placeholderFreqs.getOrElse(index) { "—" },
                color = LocalAppSkin.current.colors.onTile,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            Text(
                slot.label,
                color = LocalAppSkin.current.colors.onTile,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                slot.sublabel,
                color = LocalAppSkin.current.colors.onTile.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Industrial skin — mockup 2
// ---------------------------------------------------------------------------

@Composable
fun IndustrialPlayerScreen(
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
        NowPlayingBlock(
            state = pb,
            onSeek = actions.onSeek,
            visual = { m -> RadarVisual(m) },
        )
        Spacer(Modifier.height(10.dp))

        SkinPanel(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkinTile(onClick = {}, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text("🌙", fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    TileSub("SLEEP\nDURATION")
                }
                SkinTile(onClick = {}, modifier = Modifier.weight(1f).fillMaxHeight()) { TileSub("PATTERN 1") }
                SkinTile(onClick = {}, modifier = Modifier.weight(1f).fillMaxHeight()) { TileSub("PATTERN 2") }
                SkinTile(onClick = {}, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text("〜", fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    TileSub("NOISE\nMIXER")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1.7f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DurationControl(
                    fraction = state.sleepFraction,
                    minutes = state.sleepMinutes,
                    onChange = actions.onSleepFractionChange,
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight(),
                )
                StaticKnob(
                    label = "AURAL PATTERNS",
                    value = 0.5f,
                    size = 96.dp,
                    modifier = Modifier.weight(1f),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.width(96.dp),
                ) {
                    listOf("WHITE", "PINK", "DEEP SPACE", "AMBIENT").forEach {
                        SkinTile(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        ) { TileSub(it) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkinTile(onClick = {}, modifier = Modifier.weight(1f).fillMaxHeight()) { TileSub("PATTERN 3") }
                SkinTile(
                    onClick = {},
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    active = true,
                ) {
                    TileSub("RANDOM PATTERN")
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RotaryKnob("VOL", state.volume, actions.onVolumeChange)
            RotaryKnob("BAL", state.balance, actions.onBalanceChange)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DurationControl(
    fraction: Float,
    minutes: Int,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppSkin.current.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text("$minutes", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        VerticalSlider(
            value = fraction,
            onValueChange = onChange,
            modifier = Modifier
                .width(36.dp)
                .weight(1f)
                .padding(vertical = 4.dp),
        )
        Text(
            text = "DURATION",
            color = c.textDim,
            fontSize = 8.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun RadarVisual(modifier: Modifier) {
    val c = LocalAppSkin.current.colors
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        for (ring in 1..3) {
            drawCircle(
                color = c.accent.copy(alpha = 0.25f),
                radius = r * ring / 3f,
                center = center,
                style = Stroke(1.dp.toPx()),
            )
        }
        drawLine(
            color = c.accent,
            start = center,
            end = Offset(center.x + r * 0.9f, center.y - r * 0.5f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(c.accentAlt, radius = 3.dp.toPx(), center = Offset(center.x + r * 0.4f, center.y - r * 0.2f))
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
