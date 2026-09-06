package org.dylanjones.sleepradio.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dylanjones.sleepradio.core.design.LocalAppSkin
import org.dylanjones.sleepradio.core.design.SkinId
import org.dylanjones.sleepradio.core.design.panelBrush
import org.dylanjones.sleepradio.playback.PlaybackState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun PlayerHeader(
    onMenu: () -> Unit,
    onBell: () -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    val c = LocalAppSkin.current.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton("≡", c.textPrimary, onMenu)
        center()
        GlyphButton("🔔", c.textSecondary, onBell)
    }
}

@Composable
private fun GlyphButton(glyph: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = color, fontSize = 22.sp)
    }
}

@Composable
fun NowPlayingBlock(
    state: PlaybackState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalAppSkin.current
    val c = skin.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            val visualModifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.panelBrush())
                .border(1.dp, c.panelStroke, RoundedCornerShape(18.dp))
            // The skin picks its now-playing visual: radar for Industrial,
            // album-art placeholder for Neon. (Real album art: Phase 7.)
            if (skin.id == SkinId.INDUSTRIAL) {
                RadarVisual(visualModifier)
            } else {
                DefaultArtwork(state, visualModifier)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.fillMaxWidth()) {
                Label("Artist")
                Value(state.artist ?: "—", c.textPrimary)
                Spacer(Modifier.height(8.dp))
                Label("Track")
                Value(state.title ?: "—", c.accentAlt)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                color = c.textSecondary,
                fontSize = 12.sp,
            )
            Text(formatTime(state.durationMs), color = c.textDim, fontSize = 12.sp)
        }
        Slider(
            value = state.positionMs.coerceIn(0L, state.durationMs.coerceAtLeast(0L)).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
            enabled = state.durationMs > 0,
            modifier = Modifier.height(20.dp),
            colors = SliderDefaults.colors(
                activeTrackColor = c.accent,
                thumbColor = c.accent,
                inactiveTrackColor = c.panelStroke,
            ),
        )
        WaveformStrip(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun DefaultArtwork(state: PlaybackState, modifier: Modifier) {
    // Album art (Coil) is wired in Phase 7. Placeholder glyph for now.
    val c = LocalAppSkin.current.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("♪", color = c.textDim, fontSize = 28.sp)
    }
}

@Composable
private fun RadarVisual(modifier: Modifier) {
    val c = LocalAppSkin.current.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val r = size.minDimension / 2f * 0.82f
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
            drawCircle(
                color = c.accentAlt,
                radius = 3.dp.toPx(),
                center = Offset(center.x + r * 0.4f, center.y - r * 0.2f),
            )
        }
    }
}

@Composable
private fun WaveformStrip(modifier: Modifier) {
    val c = LocalAppSkin.current.colors
    Canvas(modifier) {
        val barW = 3.dp.toPx()
        val gap = 3.dp.toPx()
        var x = 0f
        var i = 0
        while (x < size.width) {
            val h = size.height * (0.25f + 0.75f * abs(sin(i * 0.7f)))
            drawRoundRect(
                color = if (i % 4 == 0) c.accent else c.accent.copy(alpha = 0.35f),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
            x += barW + gap
            i++
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        color = LocalAppSkin.current.colors.textDim,
        fontSize = 11.sp,
        lineHeight = 13.sp,
    )
}

@Composable
private fun Value(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 19.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
