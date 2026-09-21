package org.dylanjones.sleepradio.core.design

import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.dylanjones.sleepradio.R
import kotlin.math.roundToInt

/*
 * The radio-style dialog kit: a black-plastic faceplate with a chrome rim, chrome push buttons, a
 * lamp toggle, a slide fader and an option lamp. The frame and buttons are 9-slice bitmaps
 * (tools/keyart/make_dialog.py); the toggle, fader and lamp are drawn. RadioDialog, RadioTextButton,
 * RadioSwitch, RadioSlider and RadioLamp are drop-in replacements for AlertDialog, TextButton,
 * Switch, Slider and RadioButton.
 */

private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

private val Cream = Color(0xFFF2E9D9)
private val Amber = Color(0xFFE0A64C)
private val AmberHot = Color(0xFFFFCB74)

private val TitleStyle = TextStyle(
    fontFamily = Condensed,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    letterSpacing = 1.sp,
    shadow = Shadow(Color.Black, Offset(0f, 2f), 0f), // engraved
)
private val ButtonStyle = TextStyle(
    fontFamily = Condensed,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    letterSpacing = 1.4.sp,
)

// ---------------------------------------------------------------------------------------------
// 9-slice drawing
// ---------------------------------------------------------------------------------------------

/**
 * Stretches [image] (drawn at 3x: one art pixel is 1/3 dp) over the whole box, keeping the four
 * corners and edges at their natural size and stretching only the middle. The insets are in art pixels.
 * The optional outsets ([outL]…[outB], art pixels) let the picture's transparent shadow margin hang
 * outside the box, so the visible body of the artwork lines up with the box itself.
 */
internal fun DrawScope.drawNinePatch(
    image: ImageBitmap,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    outL: Int = 0,
    outT: Int = 0,
    outR: Int = 0,
    outB: Int = 0,
) {
    val k = density / 3f
    val ol = outL * k
    val ot = outT * k
    val w = (size.width + ol + outR * k).roundToInt()
    val h = (size.height + ot + outB * k).roundToInt()
    val dl = (left * k).roundToInt()
    val dt = (top * k).roundToInt()
    val dr = (right * k).roundToInt()
    val db = (bottom * k).roundToInt()
    translate(left = -ol, top = -ot) {
        if (w < dl + dr || h < dt + db) {
            drawImage(image, dstSize = IntSize(w, h), filterQuality = FilterQuality.High)
            return@translate
        }
        val sx = intArrayOf(0, left, image.width - right, image.width)
        val sy = intArrayOf(0, top, image.height - bottom, image.height)
        val dx = intArrayOf(0, dl, w - dr, w)
        val dy = intArrayOf(0, dt, h - db, h)
        for (iy in 0..2) for (ix in 0..2) {
            drawImage(
                image,
                srcOffset = IntOffset(sx[ix], sy[iy]),
                srcSize = IntSize(sx[ix + 1] - sx[ix], sy[iy + 1] - sy[iy]),
                dstOffset = IntOffset(dx[ix], dy[iy]),
                dstSize = IntSize(dx[ix + 1] - dx[ix], dy[iy + 1] - dy[iy]),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dialog frame
// ---------------------------------------------------------------------------------------------

/**
 * A radio-faceplate replacement for the Material AlertDialog (same slots): title, content and the
 * buttons along the bottom. The content area takes the space left over, so long content must scroll
 * itself, exactly as with AlertDialog.
 */
@Composable
fun RadioDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val frame = ImageBitmap.imageResource(R.drawable.dialog_frame)
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier.padding(horizontal = 10.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .drawBehind { drawNinePatch(frame, 64, 64, 64, 64) },
            ) {
                Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 24.dp)) {
                    if (title != null) {
                        CompositionLocalProvider(
                            LocalTextStyle provides TitleStyle,
                            LocalContentColor provides Cream,
                        ) { title() }
                        Spacer(Modifier.height(8.dp))
                        ChromeRule()
                        Spacer(Modifier.height(10.dp))
                    }
                    if (text != null) {
                        Box(Modifier.weight(1f, fill = false)) {
                            CompositionLocalProvider(LocalContentColor provides Cream) { text() }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** A thin chrome rule under a dialog title. */
@Composable
private fun ChromeRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0x00B9AC97), Color(0xFFB9AC97), Color(0xFFE6DCC8), Color(0xFFB9AC97), Color(0x00B9AC97)),
                ),
            ),
    )
}

// ---------------------------------------------------------------------------------------------
// Push button
// ---------------------------------------------------------------------------------------------

/** A chrome radio pushbutton with a text label: a drop-in for the Material TextButton. */
@Composable
fun RadioTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val out = ImageBitmap.imageResource(R.drawable.dialog_button_out)
    val pressedArt = ImageBitmap.imageResource(R.drawable.dialog_button_in)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .defaultMinSize(minWidth = 76.dp, minHeight = 46.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind { drawNinePatch(if (pressed) pressedArt else out, 40, 34, 40, 44) },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 10.dp)
                .offset(y = if (pressed) 2.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides ButtonStyle,
                LocalContentColor provides (if (pressed) AmberHot else Cream),
            ) { content() }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lamp toggle
// ---------------------------------------------------------------------------------------------

/** A recessed slot with a sliding chrome knob that lights the slot amber when on: a drop-in for Switch. */
@Composable
fun RadioSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val pos by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "switch")
    Canvas(
        modifier
            .size(width = 58.dp, height = 34.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .toggleable(
                value = checked,
                enabled = enabled && onCheckedChange != null,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = { onCheckedChange?.invoke(it) },
            ),
    ) {
        val h = size.height * 0.74f
        val top = (size.height - h) / 2f
        val r = CornerRadius(h / 2f)
        val knob = h * 0.86f
        val kx = (h - knob) / 2f + pos * (size.width - knob - (h - knob))
        // the recessed slot
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF080604), Color(0xFF1F1913))), Offset(0f, top), Size(size.width, h), r)
        // lit portion up to the knob
        if (pos > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF8A5F1C), Amber, AmberHot)),
                Offset(0f, top),
                Size(kx + knob / 2f + (h - knob) / 2f, h),
                r,
                alpha = pos * 0.9f,
            )
        }
        drawRoundRect(Color.Black, Offset(0f, top), Size(size.width, h), r, style = Stroke(1.5.dp.toPx()), alpha = 0.7f)
        drawRoundRect(Color(0x33FFECC8), Offset(0.75.dp.toPx(), top + h - 1.dp.toPx()), Size(size.width - 1.5.dp.toPx(), 1.dp.toPx()), CornerRadius(1f))
        // the chrome knob
        val cx = kx + knob / 2f
        val cy = size.height / 2f
        drawCircle(Color.Black.copy(alpha = 0.5f), knob / 2f + 1.dp.toPx(), Offset(cx, cy + 2.dp.toPx()))
        drawCircle(
            Brush.linearGradient(
                listOf(Color(0xFFFBF6EA), Color(0xFF8F8676), Color(0xFF565044), Color(0xFFB0A48D)),
                start = Offset(cx - knob / 2f, cy - knob / 2f),
                end = Offset(cx + knob / 2f, cy + knob / 2f),
            ),
            knob / 2f, Offset(cx, cy),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF3A3229), Color(0xFF15110D)),
                center = Offset(cx - knob * 0.08f, cy - knob * 0.1f),
                radius = knob * 0.38f,
            ),
            knob * 0.34f, Offset(cx, cy),
        )
        drawCircle(Color.Black.copy(alpha = 0.7f), knob / 2f, Offset(cx, cy), style = Stroke(1.dp.toPx()))
        if (checked) drawCircle(AmberHot.copy(alpha = 0.9f), knob * 0.10f, Offset(cx, cy - knob * 0.16f))
    }
}

// ---------------------------------------------------------------------------------------------
// Fader
// ---------------------------------------------------------------------------------------------

/** A radio slide fader: a recessed groove that fills amber, with tick marks and a chrome cap. A drop-in for Slider. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        thumb = { FaderCap() },
        track = { state -> FaderTrack(state) },
    )
}

@Composable
private fun FaderCap() {
    Canvas(Modifier.size(width = 22.dp, height = 36.dp)) {
        val r = CornerRadius(5.dp.toPx())
        drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(0f, 3.dp.toPx()), size, r)
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF6E665A), Color(0xFFE6DCC8), Color(0xFFA69D8B), Color(0xFF4A443A))),
            Offset.Zero, size, r,
        )
        drawRoundRect(Color.Black, Offset.Zero, size, r, style = Stroke(1.dp.toPx()), alpha = 0.7f)
        // grip lines and the amber index line
        for (i in -2..2) {
            if (i == 0) continue
            val y = size.height / 2f + i * 5.dp.toPx()
            drawLine(Color(0x99000000), Offset(4.dp.toPx(), y), Offset(size.width - 4.dp.toPx(), y), 1.2.dp.toPx())
            drawLine(Color(0x33FFFFFF), Offset(4.dp.toPx(), y + 1.2.dp.toPx()), Offset(size.width - 4.dp.toPx(), y + 1.2.dp.toPx()), 1.dp.toPx())
        }
        drawLine(Amber, Offset(4.dp.toPx(), size.height / 2f), Offset(size.width - 4.dp.toPx(), size.height / 2f), 2.dp.toPx())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaderTrack(state: SliderState) {
    Canvas(Modifier.fillMaxWidth().height(36.dp)) {
        val range = state.valueRange
        val frac = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val grooveH = 9.dp.toPx()
        val top = (size.height - grooveH) / 2f
        val r = CornerRadius(grooveH / 2f)
        // tick marks above and below the groove
        val ticks = if (state.steps in 1..40) state.steps + 2 else 11
        for (i in 0 until ticks) {
            val x = size.width * i / (ticks - 1)
            val len = if (i == 0 || i == ticks - 1) 6.dp.toPx() else 4.dp.toPx()
            drawLine(Color(0x88B9AC97), Offset(x, top - 3.dp.toPx() - len), Offset(x, top - 3.dp.toPx()), 1.dp.toPx())
            drawLine(Color(0x88B9AC97), Offset(x, top + grooveH + 3.dp.toPx()), Offset(x, top + grooveH + 3.dp.toPx() + len), 1.dp.toPx())
        }
        // the groove, then the lit part
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF050403), Color(0xFF1E1812))), Offset(0f, top), Size(size.width, grooveH), r)
        if (frac > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF8A5F1C), Amber, AmberHot)),
                Offset(0f, top), Size(size.width * frac, grooveH), r, alpha = 0.95f,
            )
        }
        drawRoundRect(Color.Black, Offset(0f, top), Size(size.width, grooveH), r, style = Stroke(1.dp.toPx()), alpha = 0.8f)
        drawRoundRect(Color(0x33FFECC8), Offset(1.dp.toPx(), top + grooveH), Size(size.width - 2.dp.toPx(), 1.dp.toPx()), CornerRadius(1f))
    }
}

// ---------------------------------------------------------------------------------------------
// Option lamp
// ---------------------------------------------------------------------------------------------

/** A chrome-ringed lamp that lights amber when [selected]: a drop-in for RadioButton. */
@Composable
fun RadioLamp(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val lit by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "lamp")
    Box(
        modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(
                enabled = enabled && onClick != null,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick?.invoke() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val c = center
            val rad = size.minDimension / 2f
            if (lit > 0f) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFFFAB3D).copy(alpha = 0.55f * lit), Color.Transparent), center = c, radius = rad * 1.7f),
                    rad * 1.7f, c,
                )
            }
            drawCircle(
                Brush.verticalGradient(listOf(Color(0xFFFBF6EA), Color(0xFF8F8676), Color(0xFF565044), Color(0xFFB0A48D))),
                rad, c,
            )
            drawCircle(Color(0xFF0A0806), rad * 0.82f, c)
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        androidx.compose.ui.graphics.lerp(Color(0xFF4A3418), Color(0xFFFFCB74), lit),
                        androidx.compose.ui.graphics.lerp(Color(0xFF150D05), Color(0xFFB57420), lit),
                    ),
                    center = Offset(c.x - rad * 0.15f, c.y - rad * 0.2f),
                    radius = rad * 0.8f,
                ),
                rad * 0.66f, c,
            )
            drawCircle(Color.White.copy(alpha = 0.25f + 0.25f * lit), rad * 0.13f, Offset(c.x - rad * 0.22f, c.y - rad * 0.28f))
            drawCircle(Color.Black.copy(alpha = 0.7f), rad, c, style = Stroke(1.dp.toPx()))
        }
    }
}
