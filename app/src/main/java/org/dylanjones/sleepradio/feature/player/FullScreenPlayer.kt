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
import org.dylanjones.sleepradio.core.design.RotaryKnob
import org.dylanjones.sleepradio.core.design.SkinPanel
import org.dylanjones.sleepradio.core.design.SteamWheel

/** The control strip's height: SLEEP and NOISE at about their size in the normal landscape view. */
private val ControlStripHeight = 64.dp

/** VOL and BAL in the control strip: the knob, with its label underneath, fits the strip's height. */
private val KnobSize = 46.dp

/**
 * The full-screen channel button ("Channel buttons" → Full screen): for poor eyesight. One channel
 * key fills most of the screen with its name as large as it fits (the upright key in portrait, the
 * broad key in landscape); swipe sideways for the next channel, tap to play/pause. The track and
 * stream information goes, and so do previous · play/pause · next. The menu wheel is top left, as in
 * the normal view. In landscape one strip runs under the key: VOL · SLEEP · NOISE · BAL; in portrait
 * VOL and BAL sit top right beside the menu wheel, and SLEEP and NOISE share the width under the key.
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
            // Height is what is scarce: the menu wheel goes in a slim column on the left, so the
            // key and the strip under it get the whole height.
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuWheel(actions)
                SkinPanel(Modifier.weight(1f).fillMaxHeight()) {
                    PresetRow(state, actions, Modifier.fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(6.dp))
                    ControlStrip(state, actions)
                }
            }
        } else {
            // Width is what is scarce: VOL and BAL go up beside the menu wheel (space that is free
            // anyway), so SLEEP and NOISE get the full width under the key.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MenuWheel(actions)
                Spacer(Modifier.weight(1f))
                RotaryKnob("VOL", state.volume, actions.onVolumeChange, size = KnobSize)
                Spacer(Modifier.width(20.dp))
                RotaryKnob("BAL", state.balance, actions.onBalanceChange, size = KnobSize)
                Spacer(Modifier.width(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            SkinPanel(Modifier.fillMaxWidth().weight(1f)) {
                PresetRow(state, actions, Modifier.fillMaxWidth().weight(1f), tall = true)
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
        Spacer(Modifier.height(4.dp))
    }
}

/** The wide key artwork's width : height, which SLEEP and NOISE keep. */
private const val KEY_ASPECT = ChannelKeyArt.WIDTH / ChannelKeyArt.HEIGHT

/** Landscape: VOL at the left edge, SLEEP and NOISE together in the middle, BAL at the right edge. */
@Composable
private fun ControlStrip(state: PlayerUiState, actions: PlayerActions) {
    Row(
        Modifier.fillMaxWidth().height(ControlStripHeight).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RotaryKnob("VOL", state.volume, actions.onVolumeChange, size = KnobSize)
        Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
            // Each keeps the key artwork's shape, as big as the strip allows (narrower on a narrow screen).
            val keyModifier = Modifier.weight(1f, fill = false).fillMaxHeight().aspectRatio(KEY_ASPECT, matchHeightConstraintsFirst = true)
            SleepKey(state, actions, keyModifier)
            NoiseKey(state, actions, keyModifier)
        }
        RotaryKnob("BAL", state.balance, actions.onBalanceChange, size = KnobSize)
    }
}

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
