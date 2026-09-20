package org.dylanjones.sleepradio.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dylanjones.sleepradio.core.design.LocalAppSkin
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
    starting: Boolean = false,
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
            // Real cover art when the item has any; otherwise a ♪ placeholder.
            val art = rememberAlbumArt(state.artworkUri, state.mediaUri)
            when {
                art != null -> Image(
                    bitmap = art,
                    contentDescription = "Cover art",
                    contentScale = ContentScale.Crop,
                    modifier = visualModifier,
                )

                else -> DefaultArtwork(visualModifier)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.fillMaxWidth()) {
                val (topLabel, topValue) = when {
                    starting -> "Station" to "SleepRadio"
                    state.isRadio -> "Station" to (state.stationName ?: state.title ?: "—")
                    state.isAudiobook -> "Book" to (state.artist ?: "—")
                    state.isPodcast -> "Podcast" to (state.artist ?: "—")
                    else -> "Artist" to (state.artist ?: "—")
                }
                val (botLabel, botValue) = when {
                    starting -> "Now playing" to "Tuning in…"
                    // ICY now-playing when the stream sends it, else the station
                    // description, else a plain "Live".
                    state.isRadio -> "Now playing" to (state.nowPlaying ?: state.artist ?: "Live")
                    state.isAudiobook -> "Chapter" to (state.title ?: "—")
                    state.isPodcast -> "Episode" to (state.title ?: "—")
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
        // One fixed layout for every source type: nothing here is added or removed when the
        // mode changes (that made the whole screen jump). The status line always has its row
        // and just cross-fades its text; the time + seek bar are always laid out and fade to a
        // dim, inert ghost when there is nothing to seek (live radio).
        StatusRow(status = statusFor(state, starting))
        Spacer(Modifier.height(4.dp))
        val seekable = !state.isRadio
        val seekAlpha by animateFloatAsState(
            targetValue = if (seekable) 1f else INACTIVE_ALPHA,
            animationSpec = tween(FADE_MS),
            label = "seek-alpha",
        )
        Column(Modifier.fillMaxWidth().alpha(seekAlpha)) {
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
                enabled = seekable && state.durationMs > 0,
                modifier = Modifier.height(20.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = c.accent,
                    thumbColor = c.accent,
                    inactiveTrackColor = c.panelStroke,
                ),
            )
        }
    }
}

/** What the one-line status row says, or null for "nothing" (it then just fades out). */
private data class StatusLine(val text: String, val kind: Kind, val extra: String? = null) {
    enum class Kind { ACCENT, ACCENT_ALT }
}

private fun statusFor(state: PlaybackState, starting: Boolean): StatusLine? = when {
    starting -> StatusLine("●  TUNING IN…", StatusLine.Kind.ACCENT)
    state.isBroadcast ->
        if (state.djSpeaking) StatusLine("🎙  ON AIR — DJ", StatusLine.Kind.ACCENT_ALT)
        else StatusLine("●  ON AIR", StatusLine.Kind.ACCENT)
    state.isRadio -> StatusLine("● LIVE", StatusLine.Kind.ACCENT, extra = if (state.isBuffering) "buffering…" else null)
    else -> null
}

/**
 * The status line ("ON AIR", "● LIVE", "TUNING IN…"). Its row is ALWAYS present at a fixed
 * height (scaled with the font size) so changing source never moves the rest of the screen;
 * the text cross-fades, and fades out entirely for sources with no status.
 */
@Composable
private fun StatusRow(status: StatusLine?) {
    val c = LocalAppSkin.current.colors
    val rowHeight = with(LocalDensity.current) { 16.sp.toDp() }
    Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.CenterStart) {
        Crossfade(targetState = status, animationSpec = tween(FADE_MS), label = "status") { line ->
            if (line != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = line.text,
                        color = if (line.kind == StatusLine.Kind.ACCENT_ALT) c.accentAlt else c.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (line.extra != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(line.extra, color = c.textDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Opacity of a control that exists in the layout but does not apply to the current source. */
private const val INACTIVE_ALPHA = 0.25f
private const val FADE_MS = 250

@Composable
private fun DefaultArtwork(modifier: Modifier) {
    val c = LocalAppSkin.current.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("♪", color = c.textDim, fontSize = 28.sp)
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
