package org.dylanjones.sleepradio.feature.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dylanjones.sleepradio.core.audio.VuLevels
import org.dylanjones.sleepradio.core.design.LocalAppSkin
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/** Analogue VU scale, in dBFS peak, mapped linearly across the needle sweep. */
private const val VU_MIN_DB = -30.0
private const val VU_MAX_DB = 0.0
private val VU_FLOOR_LIN = Math.pow(10.0, VU_MIN_DB / 20.0)

/** Fraction (0..1) of the needle sweep for a peak level (0..1). Pure — tested. */
internal fun vuNeedleFraction(peak: Float): Float {
    if (peak <= VU_FLOOR_LIN) return 0f
    val db = 20.0 * log10(peak.toDouble())
    return ((db - VU_MIN_DB) / (VU_MAX_DB - VU_MIN_DB)).toFloat().coerceIn(0f, 1f)
}

/** Where the red zone starts on the sweep (≈ −4.5 dBFS). */
private const val VU_RED_FROM = 0.85f

/** The two side-by-side L / R meters that replace the FFT strip on the Studio skin. */
@Composable
fun VuMeterPair(levels: VuLevels, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnalogVuMeter(levels.left, "L", Modifier.weight(1f).fillMaxHeight())
        AnalogVuMeter(levels.right, "R", Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun AnalogVuMeter(level: Float, label: String, modifier: Modifier) {
    val c = LocalAppSkin.current.colors
    // Snappy with a touch of overshoot — an underdamped needle, not a slow crawl.
    val frac by animateFloatAsState(
        targetValue = vuNeedleFraction(level),
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "vu-$label",
    )

    val face = Color(0xFFEDE3C9)
    val ink = Color(0xFF2A2118)
    val red = Color(0xFFB23A2A)

    Box(
        modifier
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.BottomStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(color = face)
            drawRoundRect(color = c.panelStroke, style = Stroke(1.5.dp.toPx()))

            // A landscape VU: needle pivots below the visible face so only the
            // fanned-out top of the sweep shows. Straight up = −90°.
            val pivot = Offset(size.width / 2f, size.height * 1.02f)
            val radius = size.height * 0.90f
            val sweepDeg = 90f
            val startDeg = -90f - sweepDeg / 2f

            fun pointAt(fraction: Float, r: Float): Offset {
                val a = Math.toRadians((startDeg + sweepDeg * fraction).toDouble())
                return Offset(pivot.x + r * cos(a).toFloat(), pivot.y + r * sin(a).toFloat())
            }

            // Scale ticks.
            var t = 0f
            while (t <= 1.001f) {
                val major = t < 0.02f || kotlin.math.abs(t - 0.5f) < 0.03f || t > 0.98f
                drawLine(
                    color = if (t >= VU_RED_FROM) red else ink,
                    start = pointAt(t, radius * if (major) 0.84f else 0.90f),
                    end = pointAt(t, radius),
                    strokeWidth = (if (major) 2.2f else 1.2f).dp.toPx(),
                    cap = StrokeCap.Round,
                )
                t += 0.1f
            }
            // Red band along the arc.
            drawArc(
                color = red,
                startAngle = startDeg + sweepDeg * VU_RED_FROM,
                sweepAngle = sweepDeg * (1f - VU_RED_FROM),
                useCenter = false,
                topLeft = Offset(pivot.x - radius, pivot.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )

            // Needle (pivot itself sits below the visible face).
            drawLine(
                color = ink,
                start = pivot,
                end = pointAt(frac.coerceIn(0f, 1f), radius * 0.98f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = label,
            color = ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
        )
    }
}
