package org.dylanjones.sleepradio.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f
private const val DETENT_STEP = 0.05f

/**
 * A draggable rotary knob. Drag up/down (or press arrows with TalkBack) to change
 * [value] in 0..1. Emits a light haptic tick when crossing each 5% detent.
 */
@Composable
fun RotaryKnob(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val c = LocalAppSkin.current.colors
    val haptics = LocalHapticFeedback.current
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    var lastDetent by remember { mutableFloatStateOf((value / DETENT_STEP).roundToInt().toFloat()) }
    var dragAccum by remember { mutableFloatStateOf(value) }

    fun emit(next: Float) {
        val clamped = next.coerceIn(0f, 1f)
        val detent = (clamped / DETENT_STEP).roundToInt().toFloat()
        if (detent != lastDetent) {
            lastDetent = detent
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        latestOnChange(clamped)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, 0)
                stateDescription = "${(value * 100).roundToInt()}%"
                setProgress { target -> emit(target); true }
            },
    ) {
        Canvas(
            Modifier
                .size(size)
                .pointerInput(Unit) {
                    val rangePx = 240.dp.toPx()
                    detectDragGestures(
                        onDragStart = { dragAccum = latestValue },
                        onDrag = { change, drag ->
                            change.consume()
                            dragAccum = (dragAccum - drag.y / rangePx).coerceIn(0f, 1f)
                            emit(dragAccum)
                        },
                    )
                },
        ) {
            val stroke = 6.dp.toPx()
            val radius = (this.size.minDimension - stroke) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            drawArc(
                color = c.glow.copy(alpha = 0.20f),
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = c.accent,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE * value.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawCircle(color = c.panelTop, radius = radius * 0.62f, center = center)
            drawCircle(
                color = c.panelStroke,
                radius = radius * 0.62f,
                center = center,
                style = Stroke(1.dp.toPx()),
            )
            val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * value.coerceIn(0f, 1f)).toDouble())
            drawLine(
                color = c.accent,
                start = center,
                end = Offset(
                    center.x + (radius * 0.5f) * cos(angle).toFloat(),
                    center.y + (radius * 0.5f) * sin(angle).toFloat(),
                ),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = label,
            color = c.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
