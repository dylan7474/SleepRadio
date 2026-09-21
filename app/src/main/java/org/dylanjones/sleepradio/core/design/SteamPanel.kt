package org.dylanjones.sleepradio.core.design

import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.dylanjones.sleepradio.R
import kotlin.math.roundToInt

/*
 * The steam-age instrument panel at the top of the player: a brass-and-copper faceplate with a
 * hand-wheel menu button, a riveted nameplate, a porthole for the cover art, brass label tags over
 * glass readout windows, a status lamp, a gauge and a slide-rule seek bar. The metal is bitmap art
 * (tools/keyart/make_steam.py -> res/drawable-nodpi/steam_*.webp); the words, the needle, the
 * slide-rule cursor and the lamps are drawn live over it.
 *
 * Text sizes here are dp-based (not sp): the words live inside fixed windows in a picture, so a
 * large system font size must not push them out of it.
 */

/** Geometry of the steam artwork, in art pixels (3 per dp). Mirrors `tools/keyart/steam_regions.json`. */
object SteamArt {
    /** A 9-slice: the stretch insets and the transparent shadow margin that hangs outside the layout box. */
    data class Slice(
        val left: Int, val top: Int, val right: Int, val bottom: Int,
        val outL: Int, val outT: Int, val outR: Int, val outB: Int,
    )

    val PANEL = Slice(76, 68, 76, 92, 12, 6, 12, 30)
    val PLATE = Slice(54, 48, 54, 63, 9, 3, 9, 18)
    val WINDOW = Slice(36, 33, 36, 45, 6, 3, 6, 15)
    val TAG = Slice(24, 21, 24, 27, 6, 3, 6, 9)
    val RAIL = Slice(36, 30, 36, 39, 9, 3, 9, 12)

    /** Frame thickness of the readout window, art px: the glass starts this far in. */
    const val WINDOW_GLASS = 9

    const val RING_CANVAS = 360f
    const val RING_CENTRE_X = 180f
    const val RING_CENTRE_Y = 174f

    /** Radius of the transparent hole in the porthole ring, where the cover art shows. */
    const val PORTHOLE_HOLE = 111f
    const val GAUGE_PIVOT_X = 180f
    const val GAUGE_PIVOT_Y = 186f
    const val WHEEL_CANVAS = 300f
    const val WHEEL_RIM = 132f
}

/** The pure numbers behind the gauge and the slide rule. */
object SteamMath {
    const val NEEDLE_MIN_DEG = -120f
    const val NEEDLE_SWEEP_DEG = 240f

    fun progressFraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    /** Needle angle from straight up, clockwise: -120° at the start, +120° at the end. */
    fun needleAngleDeg(fraction: Float): Float = NEEDLE_MIN_DEG + fraction.coerceIn(0f, 1f) * NEEDLE_SWEEP_DEG

    /** Where a touch at [x] falls on a slide rule [width] wide with [inset] of dead margin each end (0..1). */
    fun fractionAt(x: Float, width: Float, inset: Float): Float {
        val span = width - 2f * inset
        return if (span <= 0f) 0f else ((x - inset) / span).coerceIn(0f, 1f)
    }

    fun seekMs(fraction: Float, durationMs: Long): Long =
        (fraction.coerceIn(0f, 1f) * durationMs.coerceAtLeast(0L)).toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
}

// ---------------------------------------------------------------------------------------------
// Palette and type
// ---------------------------------------------------------------------------------------------

private val AmberHot = Color(0xFFFFCB74)
private val AmberDim = Color(0xFFA3742B)
private val AmberGlow = Color(0xFFFFAA3C)
private val PlateInk = Color(0xFF2A1804)
private val PlateInk2 = Color(0xFF3D2608)
private val PlateShine = Color(0xA6FFECAA)

private val Serif = FontFamily(Typeface.create("serif", Typeface.BOLD))
private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

/** A size in dp used as a text size, immune to the system font-size setting. */
@Composable
private fun fixedSp(dp: Float): TextUnit = with(LocalDensity.current) { dp.dp.toSp() }

private fun DrawSliceModifier(bitmap: ImageBitmap, s: SteamArt.Slice): Modifier =
    Modifier.drawBehind { drawNinePatch(bitmap, s.left, s.top, s.right, s.bottom, s.outL, s.outT, s.outR, s.outB) }

// ---------------------------------------------------------------------------------------------
// Faceplate, nameplate, tags, windows
// ---------------------------------------------------------------------------------------------

/** The dark brass-edged faceplate everything sits on. */
@Composable
fun BrassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val art = ImageBitmap.imageResource(R.drawable.steam_panel)
    Column(modifier.then(DrawSliceModifier(art, SteamArt.PANEL)), content = content)
}

/** The riveted brass nameplate: a title and a small line under it. */
@Composable
fun BrassNameplate(title: String, subtitle: String, modifier: Modifier = Modifier, scale: Float = 1f) {
    val art = ImageBitmap.imageResource(R.drawable.steam_plate)
    Box(modifier.then(DrawSliceModifier(art, SteamArt.PLATE)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = PlateInk,
                fontFamily = Serif,
                fontSize = fixedSp(20f * scale),
                lineHeight = fixedSp(22f * scale),
                letterSpacing = fixedSp(3f * scale),
                maxLines = 1,
                style = TextStyle(shadow = Shadow(PlateShine, Offset(0f, 2f), 0f)),
            )
            Text(
                text = subtitle,
                color = PlateInk2,
                fontFamily = Condensed,
                fontSize = fixedSp(8f * scale),
                lineHeight = fixedSp(10f * scale),
                letterSpacing = fixedSp(3.4f * scale),
                maxLines = 1,
                style = TextStyle(shadow = Shadow(PlateShine, Offset(0f, 2f), 0f)),
            )
        }
    }
}

/** A small brass label plate (ARTIST, TRACK…). */
@Composable
fun BrassTag(text: String, modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val art = ImageBitmap.imageResource(R.drawable.steam_tag)
    Box(
        modifier
            .height(height)
            .then(DrawSliceModifier(art, SteamArt.TAG))
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            color = PlateInk,
            fontFamily = Condensed,
            fontSize = fixedSp(9f),
            lineHeight = fixedSp(11f),
            letterSpacing = fixedSp(2f),
            maxLines = 1,
            style = TextStyle(shadow = Shadow(PlateShine, Offset(0f, 2f), 0f)),
        )
    }
}

/** A brass-framed dark glass window with amber text that scrolls when it is too long for the glass. */
@Composable
fun GlassWindow(text: String, modifier: Modifier = Modifier, height: Dp = 34.dp, textDp: Float = 19f) {
    val art = ImageBitmap.imageResource(R.drawable.steam_window)
    val lamp = LocalLampBrightness.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(DrawSliceModifier(art, SteamArt.WINDOW))
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = lerp(AmberDim, AmberHot, lamp),
            fontFamily = Condensed,
            fontSize = fixedSp(textDp),
            lineHeight = fixedSp(textDp * 22f / 19f),
            letterSpacing = fixedSp(0.9f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = TextStyle(shadow = Shadow(AmberGlow.copy(alpha = 0.85f * lamp), Offset.Zero, 16f)),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, velocity = 30.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Copper pipe
// ---------------------------------------------------------------------------------------------

/** The copper pipe under the header, with two clamp brackets. */
@Composable
fun CopperPipe(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val pipeH = 11.dp.toPx()
        val top = (size.height - pipeH) / 2f
        val copper = Brush.verticalGradient(
            listOf(Color(0xFFF6C3A0), Color(0xFFCF8558), Color(0xFF9A5530), Color(0xFFB9683C), Color(0xFF6A341A)),
            startY = top, endY = top + pipeH,
        )
        drawRect(Color.Black.copy(alpha = 0.5f), Offset(0f, top + 2.dp.toPx()), Size(size.width, pipeH))
        drawRect(copper, Offset(0f, top), Size(size.width, pipeH))
        drawLine(Color(0x66FFFFFF), Offset(0f, top + 1.5.dp.toPx()), Offset(size.width, top + 1.5.dp.toPx()), 1.dp.toPx())
        for (fx in floatArrayOf(0.22f, 0.78f)) {
            val bw = 15.dp.toPx()
            val bh = 19.dp.toPx()
            val left = size.width * fx - bw / 2f
            val bt = (size.height - bh) / 2f
            drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(left, bt + 2.dp.toPx()), Size(bw, bh), CornerRadius(3.dp.toPx()))
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFE2A47D), Color(0xFFA85F38), Color(0xFF6A341A)), startY = bt, endY = bt + bh),
                Offset(left, bt), Size(bw, bh), CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(Color(0x55FFFFFF), Offset(left + 1.dp.toPx(), bt + 1.dp.toPx()), Size(bw - 2.dp.toPx(), 1.dp.toPx()), CornerRadius(1f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hand-wheel (menu button)
// ---------------------------------------------------------------------------------------------

/** A brass valve hand-wheel: the menu button. It turns a sixth of a turn each time it is pressed. */
@Composable
fun SteamWheel(onClick: () -> Unit, contentDescription: String, size: Dp, modifier: Modifier = Modifier) {
    val art = ImageBitmap.imageResource(R.drawable.steam_wheel)
    var turns by remember { mutableIntStateOf(0) }
    val angle by animateFloatAsState(turns * 60f, tween(350), label = "wheel")
    Box(
        modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription; role = Role.Button }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                turns++
                onClick()
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // the shadow stays put while the wheel turns
            drawCircle(Color.Black.copy(alpha = 0.45f), this.size.minDimension * 0.42f, center + Offset(0f, 3.dp.toPx()))
        }
        Image(
            bitmap = art,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = angle },
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.High,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Porthole (cover art)
// ---------------------------------------------------------------------------------------------

/** The cover art in a round brass-and-copper porthole with a glass glare; a ♪ emblem when there is none. */
@Composable
fun Porthole(art: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    val ring = ImageBitmap.imageResource(R.drawable.steam_porthole)
    Box(modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val k = this.size.width / SteamArt.RING_CANVAS
            val c = Offset(SteamArt.RING_CENTRE_X * k, SteamArt.RING_CENTRE_Y * k)
            val r = (SteamArt.PORTHOLE_HOLE + 3f) * k
            val clip = Path().apply { addOval(Rect(c, r)) }
            clipPath(clip) {
                if (art != null) {
                    // centre-crop the cover to the round window
                    val side = minOf(art.width, art.height)
                    drawImage(
                        art,
                        srcOffset = androidx.compose.ui.unit.IntOffset((art.width - side) / 2, (art.height - side) / 2),
                        srcSize = IntSize(side, side),
                        dstOffset = androidx.compose.ui.unit.IntOffset((c.x - r).roundToInt(), (c.y - r).roundToInt()),
                        dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                } else {
                    // no cover: a dark glass disc with two engraved rings
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0xFF3A2A12), Color(0xFF0D0905)), center = c, radius = r),
                        r, c,
                    )
                    drawCircle(Color(0x33C8973E), r * 0.76f, c, style = Stroke(1.5.dp.toPx()))
                    drawCircle(Color(0x33C8973E), r * 0.5f, c, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        if (art == null) {
            Text(
                text = "♪",
                color = Color(0xFFB98A35).copy(alpha = 0.9f),
                fontFamily = Serif,
                fontSize = fixedSp(size.value * 0.34f),
                modifier = Modifier.align(Alignment.Center).offset(y = size * -0.01f),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawImage(ring, dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()), filterQuality = FilterQuality.High)
            glare(this, SteamArt.RING_CENTRE_X - 30f, SteamArt.RING_CENTRE_Y - 54f, 72f, 42f)
        }
    }
}

/** A soft elliptical glare, in ring-art coordinates, tilted like light on curved glass. */
private fun glare(scope: androidx.compose.ui.graphics.drawscope.DrawScope, cx: Float, cy: Float, rx: Float, ry: Float) = with(scope) {
    val k = size.width / SteamArt.RING_CANVAS
    val pivot = Offset(cx * k, cy * k)
    rotate(-24f, pivot) {
        scale(1f, ry / rx, pivot) {
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.08f), Color.Transparent), center = pivot, radius = rx * k),
                rx * k, pivot,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Gauge
// ---------------------------------------------------------------------------------------------

/** A cream-dialled gauge with a black needle and a copper counterweight; [fraction] is 0..1 along the sweep. */
@Composable
fun SteamGauge(fraction: Float, size: Dp, modifier: Modifier = Modifier) {
    val face = ImageBitmap.imageResource(R.drawable.steam_gauge)
    Box(modifier.size(size)) {
        Image(face, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High)
        Text(
            text = "TRACK",
            color = Color(0xFF5A3A10),
            fontFamily = Condensed,
            fontSize = fixedSp(size.value * 0.07f),
            letterSpacing = fixedSp(size.value * 0.02f),
            modifier = Modifier.align(Alignment.Center).offset(y = size * 0.245f),
        )
        Canvas(Modifier.fillMaxSize()) {
            val k = this.size.width / SteamArt.RING_CANVAS
            val pivot = Offset(SteamArt.GAUGE_PIVOT_X * k, SteamArt.GAUGE_PIVOT_Y * k)
            val angle = SteamMath.needleAngleDeg(fraction)
            fun needle(dx: Float, dy: Float) = Path().apply {
                moveTo(pivot.x + dx, pivot.y + dy)
                lineTo(pivot.x + dx - 6f * k, pivot.y + dy - 102f * k)
                lineTo(pivot.x + dx, pivot.y + dy - 126f * k)
                lineTo(pivot.x + dx + 6f * k, pivot.y + dy - 102f * k)
                close()
            }
            fun tail(dx: Float, dy: Float) = Path().apply {
                moveTo(pivot.x + dx, pivot.y + dy)
                lineTo(pivot.x + dx - 9f * k, pivot.y + dy + 30f * k)
                lineTo(pivot.x + dx, pivot.y + dy + 48f * k)
                lineTo(pivot.x + dx + 9f * k, pivot.y + dy + 30f * k)
                close()
            }
            rotate(angle, pivot) {
                drawPath(needle(2f * k, 4f * k), Color.Black.copy(alpha = 0.3f))   // its shadow
                drawPath(needle(0f, 0f), Color(0xFF1A1006))
                drawPath(tail(0f, 0f), Color(0xFFB9683C))
                drawPath(tail(0f, 0f), Color(0xFF3A1A0C), style = Stroke(1.dp.toPx()))
            }
            // hub
            drawCircle(Color.Black.copy(alpha = 0.4f), 16f * k, pivot + Offset(0f, 3f * k))
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFF2C0), Color(0xFFC8973E), Color(0xFF5A3C0C)), center = pivot - Offset(4f * k, 5f * k), radius = 20f * k),
                15.6f * k, pivot,
            )
            drawCircle(Color(0xFF3A2608), 15.6f * k, pivot, style = Stroke(1.dp.toPx()))
            glare(this, SteamArt.RING_CENTRE_X - 30f, SteamArt.RING_CENTRE_Y - 54f, 72f, 40f)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Status lamp
// ---------------------------------------------------------------------------------------------

/** A brass-ringed lamp lens that lights amber (scaled by the lamp-brightness setting). */
@Composable
fun SteamLamp(lit: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(if (lit) 1f else 0f, tween(250), label = "steam-lamp")
    val lamp = LocalLampBrightness.current
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        if (level > 0f) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFB347).copy(alpha = 0.7f * level * lamp), Color.Transparent), center = c, radius = r * 1.9f),
                r * 1.9f, c,
            )
        }
        drawCircle(Color.Black.copy(alpha = 0.5f), r, c + Offset(0f, 1.5.dp.toPx()))
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFBE7A6), Color(0xFFA5741F), Color(0xFF6D4812))), r, c)         // brass ring
        drawCircle(Color(0xFF15100A), r * 0.78f, c)
        val lens = r * 0.66f
        drawCircle(
            Brush.radialGradient(
                listOf(
                    lerp(Color(0xFF3B2A12), Color(0xFFFFF0C8), level * lamp),
                    lerp(Color(0xFF120C04), Color(0xFFB57420), level * lamp),
                ),
                center = c - Offset(lens * 0.2f, lens * 0.3f), radius = lens * 1.3f,
            ),
            lens, c,
        )
        drawCircle(Color.White.copy(alpha = 0.25f + 0.3f * level * lamp), lens * 0.22f, c - Offset(lens * 0.32f, lens * 0.4f))
        drawCircle(Color(0xFF2F1F07), r, c, style = Stroke(1.dp.toPx()))
    }
}

// ---------------------------------------------------------------------------------------------
// Slide rule (the seek bar)
// ---------------------------------------------------------------------------------------------

private val RuleInset = 8.dp

/**
 * A brass slide-rule seek bar: etched ticks, an amber-filled groove and a glass cursor with a
 * hairline. Tap or drag anywhere along it; [onSeekFraction] gets 0..1. [valueText] is what a screen
 * reader says ("1:29 of 4:07").
 */
@Composable
fun SlideRule(
    fraction: Float,
    enabled: Boolean,
    onSeekFraction: (Float) -> Unit,
    valueText: String,
    modifier: Modifier = Modifier,
) {
    val rail = ImageBitmap.imageResource(R.drawable.steam_rail)
    val lamp = LocalLampBrightness.current
    val seek by rememberUpdatedState(onSeekFraction)
    Box(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .semantics {
                this.contentDescription = "Seek"
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) setProgress { target -> seek(target.coerceIn(0f, 1f)); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val inset = RuleInset.toPx()
                detectTapGestures { off -> seek(SteamMath.fractionAt(off.x, size.width.toFloat(), inset)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val inset = RuleInset.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { off -> seek(SteamMath.fractionAt(off.x, size.width.toFloat(), inset)) },
                ) { change, _ ->
                    change.consume()
                    seek(SteamMath.fractionAt(change.position.x, size.width.toFloat(), inset))
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(34.dp)
                .then(DrawSliceModifier(rail, SteamArt.RAIL)),
        )
        Canvas(Modifier.fillMaxSize()) {
            val inset = RuleInset.toPx()
            val span = size.width - 2 * inset
            val railTop = (size.height - 34.dp.toPx()) / 2f
            val etch = Color(0xFF3A2608).copy(alpha = 0.85f)
            // etched scale: long marks every 10 %, short every 2.5 %
            for (i in 0..40) {
                val x = inset + span * i / 40f
                val long = i % 4 == 0
                val len = (if (long) 7.dp else 4.dp).toPx()
                drawLine(etch, Offset(x, railTop + 3.dp.toPx()), Offset(x, railTop + 3.dp.toPx() + len), if (long) 1.2.dp.toPx() else 0.8.dp.toPx())
                if (i % 2 == 0) drawLine(etch, Offset(x, railTop + 34.dp.toPx() - 3.dp.toPx()), Offset(x, railTop + 34.dp.toPx() - 3.dp.toPx() - 3.dp.toPx()), 0.8.dp.toPx())
            }
            // the groove and its amber fill
            val gTop = railTop + 13.dp.toPx()
            val gH = 8.dp.toPx()
            val gr = CornerRadius(gH / 2f)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF050403), Color(0xFF1E1812)), startY = gTop, endY = gTop + gH), Offset(inset, gTop), Size(span, gH), gr)
            val cx = inset + span * fraction.coerceIn(0f, 1f)
            if (fraction > 0f) {
                drawRoundRect(Color(0xFFFFAA3C).copy(alpha = 0.25f * lamp), Offset(inset - 1.dp.toPx(), gTop - 2.dp.toPx()), Size(cx - inset + 2.dp.toPx(), gH + 4.dp.toPx()), CornerRadius(gH / 2f + 2.dp.toPx()))
                // the gradient spans the FILLED part only, so the fill always ends in bright amber at the cursor
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFF8A5F1C), Color(0xFFE0A64C), Color(0xFFFFCB74)), startX = inset, endX = maxOf(cx, inset + 1f)),
                    Offset(inset, gTop), Size(cx - inset, gH), gr, alpha = 0.6f + 0.4f * lamp,
                )
            }
            drawRoundRect(Color.Black.copy(alpha = 0.8f), Offset(inset, gTop), Size(span, gH), gr, style = Stroke(1.dp.toPx()))
            drawRoundRect(Color(0x59FFECAA), Offset(inset + 1.dp.toPx(), gTop + gH), Size(span - 2.dp.toPx(), 1.dp.toPx()), CornerRadius(1f))

            // the cursor: a glass carriage with a brass frame, a red hairline and a copper knob
            val cw = 20.dp.toPx()
            val cl = cx - cw / 2f
            val cr = CornerRadius(5.dp.toPx())
            drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(cl, 3.dp.toPx()), Size(cw, size.height), cr)
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0x47FFFFFF), Color(0x0FFFFFFF), Color(0x29FFFFFF)), startX = cl, endX = cl + cw), Offset(cl, 0f), Size(cw, size.height), cr)
            drawRoundRect(Color(0xFF3A2608), Offset(cl, 0f), Size(cw, size.height), cr, style = Stroke(3.5.dp.toPx()))
            drawRoundRect(Color(0xFFC8973E), Offset(cl + 0.5.dp.toPx(), 0.5.dp.toPx()), Size(cw - 1.dp.toPx(), size.height - 1.dp.toPx()), cr, style = Stroke(2.5.dp.toPx()))
            drawLine(Color(0xFFFF6A48), Offset(cx, 7.dp.toPx()), Offset(cx, size.height - 7.dp.toPx()), 1.dp.toPx())
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFF6C3A0), Color(0xFFB9683C), Color(0xFF6A341A)), center = Offset(cx - 1.5.dp.toPx(), 1.5.dp.toPx()), radius = 5.dp.toPx()),
                4.5.dp.toPx(), Offset(cx, 2.dp.toPx()),
            )
        }
    }
}
