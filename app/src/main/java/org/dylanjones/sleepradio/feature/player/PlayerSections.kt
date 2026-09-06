package org.dylanjones.sleepradio.feature.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dylanjones.sleepradio.core.design.LocalAppSkin
import org.dylanjones.sleepradio.core.design.SkinId
import org.dylanjones.sleepradio.core.design.panelBrush
import org.dylanjones.sleepradio.playback.PlaybackState
import java.util.Locale

@Composable
fun PlayerHeader(
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    val c = LocalAppSkin.current.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton("≡", c.textPrimary, "Open menu", onMenu)
        center()
        // Balance the ≡ button so the wordmark stays centred.
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun GlyphButton(glyph: String, color: Color, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick, onClickLabel = description),
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
            // Real cover art when the item has any; otherwise the skin's visual —
            // an animated radar for Industrial, a ♪ placeholder for Neon.
            val art = rememberAlbumArt(state.artworkUri, state.mediaUri)
            when {
                art != null -> Image(
                    bitmap = art,
                    contentDescription = "Cover art",
                    contentScale = ContentScale.Crop,
                    modifier = visualModifier,
                )

                skin.id == SkinId.INDUSTRIAL -> RadarVisual(visualModifier)
                else -> DefaultArtwork(visualModifier)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.fillMaxWidth()) {
                val (topLabel, topValue) = when {
                    state.isRadio -> "Station" to (state.stationName ?: state.title ?: "—")
                    state.isAudiobook -> "Book" to (state.artist ?: "—")
                    else -> "Artist" to (state.artist ?: "—")
                }
                val (botLabel, botValue) = when {
                    // ICY now-playing when the stream sends it, else the station
                    // description, else a plain "Live".
                    state.isRadio -> "Now playing" to (state.nowPlaying ?: state.artist ?: "Live")
                    state.isAudiobook -> "Chapter" to (state.title ?: "—")
                    else -> "Track" to (state.title ?: "—")
                }
                Label(topLabel)
                Value(topValue, c.textPrimary)
                Spacer(Modifier.height(8.dp))
                Label(botLabel)
                Value(botValue, c.accentAlt)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.isRadio) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("● LIVE", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                if (state.isBuffering) {
                    Text("buffering…", color = c.textDim, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
        } else {
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
        }
        AudioVisualizerStrip(
            playing = state.isPlaying,
            color = c.accent,
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun DefaultArtwork(modifier: Modifier) {
    val c = LocalAppSkin.current.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("♪", color = c.textDim, fontSize = 28.sp)
    }
}

@Composable
private fun RadarVisual(modifier: Modifier) {
    val c = LocalAppSkin.current.colors
    val sweep by rememberInfiniteTransition(label = "radar").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radar-sweep",
    )
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
            // Rotating sweep line + blip.
            rotate(degrees = sweep, pivot = center) {
                drawLine(
                    color = c.accent,
                    start = center,
                    end = Offset(center.x + r, center.y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(
                color = c.accentAlt,
                radius = 3.dp.toPx(),
                center = Offset(center.x + r * 0.4f, center.y - r * 0.2f),
            )
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
    // Long titles / ICY metadata scroll horizontally (marquee) instead of being
    // clipped at the screen edge. basicMarquee only animates when the text
    // actually overflows its width.
    Text(
        text = text,
        color = color,
        fontSize = 19.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 1500,
                velocity = 32.dp,
            ),
    )
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
