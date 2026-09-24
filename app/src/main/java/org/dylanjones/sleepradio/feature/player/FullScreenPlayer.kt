package org.dylanjones.sleepradio.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dylanjones.sleepradio.core.design.ChannelKeyArt
import org.dylanjones.sleepradio.core.design.RoundGlyph
import org.dylanjones.sleepradio.core.design.RoundKey
import org.dylanjones.sleepradio.core.design.SkinPanel
import org.dylanjones.sleepradio.core.design.SteamWheel

/**
 * What the full-screen channel button shows besides the button itself. Each is one switch, as the
 * extras are still being tried out: SLEEP and NOISE may go, and the transport (previous · play/pause
 * · next) may come in. The menu wheel always stays top left, so the settings stay reachable.
 */
internal object FullScreenExtras {
    const val SLEEP_AND_NOISE = true
    const val TRANSPORT = false
}

/** SLEEP and NOISE's height in landscape: about their size in the normal landscape view. */
private val LandscapeKeyHeight = 64.dp

/**
 * The full-screen channel button ("Channel buttons" → Full screen): for poor eyesight. One channel
 * key fills most of the screen with its name as large as it fits (the upright key in portrait, the
 * wide key in landscape); swipe sideways for the next channel, tap to play/pause. The track and
 * stream information goes. The menu wheel is top left, as in the normal view; SLEEP and NOISE keep
 * their normal size.
 *
 * Swiping between channels never moves anything else: every page is the same screen.
 */
@Composable
internal fun FullScreenPlayer(
    state: PlayerUiState,
    actions: PlayerActions,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        if (landscape) {
            // The wide key as big as the height allows, SLEEP and NOISE underneath it (as in
            // portrait), and the menu wheel in a slim column on the left.
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuWheel(actions)
                SkinPanel(Modifier.weight(1f).fillMaxHeight()) {
                    PresetRow(state, actions, Modifier.fillMaxWidth().weight(1f))
                    if (FullScreenExtras.SLEEP_AND_NOISE) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth().height(LandscapeKeyHeight),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        ) {
                            SleepKey(state, actions, Modifier.width(LandscapeKeyHeight * KEY_ASPECT).fillMaxHeight())
                            NoiseKey(state, actions, Modifier.width(LandscapeKeyHeight * KEY_ASPECT).fillMaxHeight())
                        }
                    }
                }
            }
        } else {
            MenuWheel(actions)
            Spacer(Modifier.height(6.dp))
            SkinPanel(Modifier.fillMaxWidth().weight(1f)) {
                PresetRow(state, actions, Modifier.fillMaxWidth().weight(1f), tall = true)
                if (FullScreenExtras.SLEEP_AND_NOISE) {
                    Spacer(Modifier.height(8.dp))
                    // Two keys side by side, each with the key artwork's own shape.
                    Row(
                        Modifier.fillMaxWidth().aspectRatio(2 * KEY_ASPECT),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SleepKey(state, actions, Modifier.weight(1f).fillMaxHeight())
                        NoiseKey(state, actions, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
        if (FullScreenExtras.TRANSPORT) {
            Spacer(Modifier.height(10.dp))
            FullScreenTransport(state, actions)
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** The wide key artwork's width : height, which SLEEP and NOISE keep. */
private const val KEY_ASPECT = ChannelKeyArt.WIDTH / ChannelKeyArt.HEIGHT

/** The brass menu wheel, top left, the same size as in the normal view. */
@Composable
private fun MenuWheel(actions: PlayerActions) {
    SteamWheel(
        onClick = actions.onMenu,
        contentDescription = "Open menu",
        size = 46.dp,
        modifier = Modifier.padding(start = 10.dp, top = 8.dp),
    )
}

/** Previous · play/pause · next, the normal view's keys at their normal size (no VOL/BAL knobs). */
@Composable
private fun FullScreenTransport(state: PlayerUiState, actions: PlayerActions) {
    val pb = state.playback
    val skipTransport = pb.isAudiobook || pb.isPodcast
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundKey(
            glyph = RoundGlyph.PREVIOUS,
            size = 48.dp,
            lit = false,
            contentDescription = if (skipTransport) "Back one minute" else "Previous",
            onClick = actions.onPrevious,
            enabled = skipTransport || pb.hasPrevious,
        )
        RoundKey(
            glyph = if (pb.isPlaying) RoundGlyph.PAUSE else RoundGlyph.PLAY,
            size = 62.dp,
            lit = pb.isPlaying,
            contentDescription = if (pb.isPlaying) "Pause" else "Play",
            onClick = actions.onPlayPause,
        )
        RoundKey(
            glyph = RoundGlyph.NEXT,
            size = 48.dp,
            lit = false,
            contentDescription = if (skipTransport) "Forward one minute" else "Next",
            onClick = actions.onNext,
            enabled = skipTransport || pb.hasNext,
        )
    }
}
