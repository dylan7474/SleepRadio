package org.dylanjones.sleepradio.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import org.dylanjones.sleepradio.core.audio.VuLevels
import org.dylanjones.sleepradio.core.design.RotaryKnob
import org.dylanjones.sleepradio.core.design.RoundGlyph
import org.dylanjones.sleepradio.core.design.RoundKey

/** The channel keys' artwork shape (art px): a 2 x 2 block of them, or one big key, has this same outline. */
private const val KEY_ART_W = 560f
private const val KEY_ART_H = 236f

/** Height of the page-marker row under the channel block (see PresetRow). */
private val MarkerRowHeight = 16.dp

/**
 * The player turned on its side — the same ingredients as the portrait screen, arranged for a
 * wide, short display:
 *
 *  - a brass instrument strip across the top ([LandscapeTopPanel]);
 *  - in the middle the channel block (2 x 2 keys, or one big key, same outline either way) with
 *    SLEEP and its VU meter on the left and NOISE and its VU meter on the right;
 *  - the transport along the bottom: VOL · PREVIOUS · PLAY · NEXT · BAL.
 *
 * Height is what is scarce (a 360 dp phone loses more of it to the system bars), so everything is
 * sized from the height that is actually available.
 */
@Composable
internal fun LandscapePlayer(
    state: PlayerUiState,
    actions: PlayerActions,
    vu: StateFlow<VuLevels>,
    modifier: Modifier = Modifier,
) {
    val pb = state.playback
    val levels by vu.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // 0 on a short display (≈ 280 dp usable) up to 1 on a roomy one (≈ 380 dp).
        val t = ((maxHeight - 280.dp) / 100.dp).coerceIn(0f, 1f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            LandscapeTopPanel(
                state = pb,
                starting = state.broadcastStarting,
                onMenu = actions.onMenu,
                onSeek = actions.onSeek,
                t = t,
            )
            Spacer(Modifier.height(4.dp))

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val gap = 8.dp
                // The block's outline is fixed by the key artwork, so its width follows the height.
                val blockWidth = ((maxHeight - MarkerRowHeight) * (KEY_ART_W / KEY_ART_H))
                    .coerceAtMost(maxWidth - (gap * 2) - 240.dp)
                val sideWidth = (maxWidth - blockWidth - gap * 2) / 2
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.width(sideWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SleepKey(state, actions, Modifier.fillMaxWidth().weight(0.55f))
                        AnalogVuMeter(levels.left, "L", Modifier.fillMaxWidth().weight(0.45f))
                    }
                    PresetRow(state, actions, Modifier.width(blockWidth).fillMaxHeight())
                    Column(Modifier.width(sideWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        NoiseKey(state, actions, Modifier.fillMaxWidth().weight(0.55f))
                        AnalogVuMeter(levels.right, "R", Modifier.fillMaxWidth().weight(0.45f))
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            val skipTransport = pb.isAudiobook || pb.isPodcast
            val small = lerp(40.dp, 48.dp, t)
            val big = lerp(50.dp, 60.dp, t)
            val knob = lerp(38.dp, 48.dp, t)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RotaryKnob("VOL", state.volume, actions.onVolumeChange, size = knob)
                RoundKey(
                    glyph = RoundGlyph.PREVIOUS,
                    size = small,
                    lit = false,
                    contentDescription = if (skipTransport) "Back one minute" else "Previous",
                    onClick = actions.onPrevious,
                    enabled = skipTransport || pb.hasPrevious,
                )
                RoundKey(
                    glyph = if (pb.isPlaying) RoundGlyph.PAUSE else RoundGlyph.PLAY,
                    size = big,
                    lit = pb.isPlaying,
                    contentDescription = if (pb.isPlaying) "Pause" else "Play",
                    onClick = actions.onPlayPause,
                )
                RoundKey(
                    glyph = RoundGlyph.NEXT,
                    size = small,
                    lit = false,
                    contentDescription = if (skipTransport) "Forward one minute" else "Next",
                    onClick = actions.onNext,
                    enabled = skipTransport || pb.hasNext,
                )
                RotaryKnob("BAL", state.balance, actions.onBalanceChange, size = knob)
            }
        }
    }
}
