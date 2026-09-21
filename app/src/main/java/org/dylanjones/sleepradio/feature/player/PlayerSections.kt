package org.dylanjones.sleepradio.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.dylanjones.sleepradio.core.design.BrassNameplate
import org.dylanjones.sleepradio.core.design.BrassPanel
import org.dylanjones.sleepradio.core.design.BrassTag
import org.dylanjones.sleepradio.core.design.CopperPipe
import org.dylanjones.sleepradio.core.design.GlassWindow
import org.dylanjones.sleepradio.core.design.LocalLampBrightness
import org.dylanjones.sleepradio.core.design.Porthole
import org.dylanjones.sleepradio.core.design.SlideRule
import org.dylanjones.sleepradio.core.design.SteamGauge
import org.dylanjones.sleepradio.core.design.SteamLamp
import org.dylanjones.sleepradio.core.design.SteamMath
import org.dylanjones.sleepradio.core.design.SteamWheel
import org.dylanjones.sleepradio.playback.PlaybackState
import java.util.Locale

/** The two readout lines (label + value each) for the current source. */
internal data class Readouts(val label1: String, val value1: String, val label2: String, val value2: String)

internal fun readoutsFor(state: PlaybackState, starting: Boolean): Readouts = when {
    starting -> Readouts("Station", "SleepRadio", "Now playing", "Tuning in…")
    // ICY now-playing when the stream sends it, else the station description, else a plain "Live".
    state.isRadio -> Readouts(
        "Station", state.stationName ?: state.title ?: "—",
        "Now playing", state.nowPlaying ?: state.artist ?: "Live",
    )
    state.isAudiobook -> Readouts("Book", state.artist ?: "—", "Chapter", state.title ?: "—")
    state.isPodcast -> Readouts("Podcast", state.artist ?: "—", "Episode", state.title ?: "—")
    else -> Readouts("Artist", state.artist ?: "—", "Track", state.title ?: "—")
}

/** What the status lamp row says, or null for "nothing" (the lamp goes dark and the words fade out). */
internal fun statusTextFor(state: PlaybackState, starting: Boolean): String? = when {
    starting -> "TUNING IN"
    state.isBroadcast -> if (state.djSpeaking) "ON AIR — DJ" else "ON AIR"
    state.isRadio -> if (state.isBuffering) "LIVE · BUFFERING" else "LIVE"
    else -> null
}

/**
 * The top of the screen as a brass-and-copper instrument panel (see core/design/SteamPanel.kt):
 * hand-wheel menu button and nameplate, cover art in a porthole, ARTIST/TRACK readout windows, a
 * status lamp, a gauge and a slide-rule seek bar.
 *
 * One fixed layout for every source type: nothing here is added or removed when the source
 * changes. The status lamp row always has its place and just lights or goes dark; the gauge, time
 * and slide rule stay laid out and fade to a dim, inert ghost when there is nothing to seek (live radio).
 */
@Composable
fun TopPanel(
    state: PlaybackState,
    starting: Boolean,
    onMenu: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readouts = readoutsFor(state, starting)
    val status = statusTextFor(state, starting)
    val seekable = !state.isRadio && state.durationMs > 0
    val fraction = if (seekable) SteamMath.progressFraction(state.positionMs, state.durationMs) else 0f
    val art = rememberAlbumArt(state.artworkUri, state.mediaUri)
    val ghost by animateFloatAsState(if (!state.isRadio) 1f else INACTIVE_ALPHA, tween(FADE_MS), label = "seek-ghost")

    BrassPanel(modifier.fillMaxWidth()) {
        // header: hand-wheel menu button, nameplate, and a spacer that keeps the nameplate centred
        Row(
            Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SteamWheel(onClick = onMenu, contentDescription = "Open menu", size = 46.dp)
            BrassNameplate("SLEEPRADIO", "WIRELESS PLAYER", Modifier.weight(1f).height(50.dp))
            Spacer(Modifier.size(46.dp))
        }
        CopperPipe(Modifier.padding(top = 2.dp))

        // cover art + the two readout windows
        Row(
            Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Porthole(art = art, size = 98.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    BrassTag(readouts.label1)
                    GlassWindow(readouts.value1)
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    BrassTag(readouts.label2)
                    GlassWindow(readouts.value2)
                }
            }
        }

        // gauge + status lamp, time and slide rule
        Row(
            Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SteamGauge(fraction = fraction, size = 98.dp, modifier = Modifier.alpha(ghost))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                StatusRow(status)
                TimeReadout(state, seekable, Modifier.alpha(ghost))
                SlideRule(
                    fraction = fraction,
                    enabled = seekable,
                    onSeekFraction = { f -> onSeek(SteamMath.seekMs(f, state.durationMs)) },
                    valueText = "${formatTime(state.positionMs)} of ${formatTime(state.durationMs)}",
                    modifier = Modifier.alpha(ghost),
                )
            }
        }
    }
}

/** The lamp and its words. The row is always laid out; only the lamp and the text change. */
@Composable
private fun StatusRow(status: String?) {
    val lamp = LocalLampBrightness.current
    Row(
        Modifier.fillMaxWidth().height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SteamLamp(lit = status != null, size = 16.dp, modifier = Modifier.padding(start = 3.dp))
        Spacer(Modifier.width(10.dp))
        Crossfade(targetState = status, animationSpec = tween(FADE_MS), label = "status") { text ->
            if (text != null) {
                Text(
                    text = text,
                    color = Color(0xFFFFCB74),
                    fontFamily = SteamCondensed,
                    fontSize = with(LocalDensity.current) { 13.dp.toSp() },
                    lineHeight = with(LocalDensity.current) { 14.dp.toSp() },
                    letterSpacing = with(LocalDensity.current) { 2.6.dp.toSp() },
                    style = TextStyle(shadow = Shadow(Color(0xFFFFAA3C).copy(alpha = 0.7f * lamp), Offset.Zero, 14f)),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The big amber elapsed time and the smaller total. */
@Composable
private fun TimeReadout(state: PlaybackState, seekable: Boolean, modifier: Modifier = Modifier) {
    val lamp = LocalLampBrightness.current
    val d = LocalDensity.current
    Row(modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            text = if (seekable) formatTime(state.positionMs) else "0:00",
            color = Color(0xFFFFCB74),
            fontSize = with(d) { 30.dp.toSp() },
            lineHeight = with(d) { 32.dp.toSp() },
            fontFamily = SteamCondensed,
            letterSpacing = with(d) { 1.dp.toSp() },
            style = TextStyle(shadow = Shadow(Color(0xFFFFAA3C).copy(alpha = 0.8f * lamp), Offset.Zero, 20f)),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "/ " + if (seekable) formatTime(state.durationMs) else "0:00",
            color = Color(0xFFA3742B),
            fontSize = with(d) { 16.dp.toSp() },
            lineHeight = with(d) { 20.dp.toSp() },
            fontFamily = SteamCondensed,
            maxLines = 1,
        )
    }
}

/** The phone's built-in condensed bold face, used for the panel's amber readouts. */
// lazy: creating a Typeface needs Android, so it must not run when plain JVM unit tests load this file
private val SteamCondensed by lazy { FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)) }

/** Opacity of a part that exists in the layout but does not apply to the current source. */
private const val INACTIVE_ALPHA = 0.28f
private const val FADE_MS = 250

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
