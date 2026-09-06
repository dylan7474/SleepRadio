package org.dylanjones.sleepradio.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A draggable vertical slider, value 0..1, filled from the bottom. */
@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppSkin.current.colors
    val latestOnChange by rememberUpdatedState(onValueChange)

    Canvas(
        modifier
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, 0)
                setProgress { target -> latestOnChange(target.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    latestOnChange((1f - pos.y / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    latestOnChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                }
            },
    ) {
        val x = size.width / 2f
        val track = 6.dp.toPx()
        val thumbY = size.height * (1f - value.coerceIn(0f, 1f))
        drawLine(
            color = c.panelStroke,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = track,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = c.accent.copy(alpha = 0.6f),
            start = Offset(x, size.height),
            end = Offset(x, thumbY),
            strokeWidth = track,
            cap = StrokeCap.Round,
        )
        drawCircle(color = c.accent, radius = 9.dp.toPx(), center = Offset(x, thumbY))
        drawCircle(
            color = c.panelStroke,
            radius = 9.dp.toPx(),
            center = Offset(x, thumbY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
        )
    }
}

/** Maps a 0..1 fraction to a labelled minute value on the sleep-duration scale. */
fun sleepMinutesFor(fraction: Float): Int {
    val minutes = 15 + fraction.coerceIn(0f, 1f) * (60 - 15)
    return (minutes / 5f).roundToInt() * 5
}
