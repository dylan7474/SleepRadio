package org.dylanjones.sleepradio.core.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import kotlin.math.cos
import kotlin.math.sin

/** Full-bleed skin background; also publishes the skin to descendants. */
@Composable
fun SkinBackground(
    skin: AppSkin,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalAppSkin provides skin) {
        Box(
            modifier
                .fillMaxSize()
                .background(skin.colors.screenBrush()),
            content = content,
        )
    }
}

/** The skeuomorphic device housing that holds the feature controls. */
@Composable
fun SkinPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppSkin.current.colors
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(c.panelBrush())
            .border(1.dp, c.panelStroke, RoundedCornerShape(28.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

/** A pressable control tile (preset slot, SLEEP, NOISE, …). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SkinTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppSkin.current.colors
    val clickModifier =
        if (onLongClick != null) {
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        } else {
            Modifier.clickable(onClick = onClick)
        }
    val a11y = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Column(
        modifier
            .then(a11y)
            .clip(RoundedCornerShape(14.dp))
            .background(c.tileBrush(active))
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) c.tileStroke else c.panelStroke,
                shape = RoundedCornerShape(14.dp),
            )
            .then(clickModifier)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** Non-interactive knob visual. Phase 3 replaces this with a draggable control. */
@Composable
fun StaticKnob(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val c = LocalAppSkin.current.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Canvas(Modifier.size(size)) {
            val stroke = 6.dp.toPx()
            val radius = (this.size.minDimension - stroke) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawArc(
                color = c.glow.copy(alpha = 0.20f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = c.accent,
                startAngle = 135f,
                sweepAngle = 270f * value.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawCircle(color = c.panelTop, radius = radius * 0.62f, center = center)
            drawCircle(
                color = c.panelStroke,
                radius = radius * 0.62f,
                center = center,
                style = Stroke(1.dp.toPx()),
            )
            val angle = Math.toRadians((135f + 270f * value.coerceIn(0f, 1f)).toDouble())
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
        )
    }
}

/** Small round glyph button used for prev / next. */
@Composable
fun CircleGlyphButton(
    glyph: String,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val c = LocalAppSkin.current.colors
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.panelTop)
            .border(1.dp, c.panelStroke, CircleShape)
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = if (enabled) c.textPrimary else c.textDim,
            fontSize = 18.sp,
        )
    }
}

/** The big glowing play/pause disc, with its own pulsing ring. */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 84.dp,
) {
    val c = LocalAppSkin.current.colors
    val pulse = rememberInfiniteTransition(label = "ring")
    val glow by pulse.animateFloat(
        initialValue = if (isPlaying) 0.15f else 0.28f,
        targetValue = if (isPlaying) 0.55f else 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(c.glow.copy(alpha = glow), Color.Transparent)),
                ),
        )
        Box(
            Modifier
                .size(size * 0.83f)
                .clip(CircleShape)
                .background(c.panelTop)
                .border(2.dp, c.accent, CircleShape)
                .clickable(
                    onClickLabel = if (isPlaying) "Pause" else "Play",
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isPlaying) "⏸" else "▶",
                color = c.textPrimary,
                fontSize = (size.value * 0.31f).sp,
            )
        }
    }
}

/** prev · big glowing play/pause · next */
@Composable
fun TransportCluster(
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    isAudiobook: Boolean = false,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleGlyphButton(
            "⏮",
            contentDescription = if (isAudiobook) "Back one minute" else "Previous",
            onClick = onPrevious,
            enabled = hasPrevious,
        )
        PlayPauseButton(isPlaying = isPlaying, onClick = onPlayPause)
        CircleGlyphButton(
            "⏭",
            contentDescription = if (isAudiobook) "Forward one minute" else "Next",
            onClick = onNext,
            enabled = hasNext,
        )
    }
}

/** SLEEPRADIO / PLAYER wordmark. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val c = LocalAppSkin.current.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = "SLEEPRADIO",
            color = c.accent,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "PLAYER",
            color = c.textSecondary,
            fontSize = 12.sp,
            letterSpacing = 8.sp,
            textAlign = TextAlign.Center,
        )
    }
}
