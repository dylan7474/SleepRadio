package org.dylanjones.sleepradio.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.dylanjones.sleepradio.R

/** The symbol on a [RoundKey]. */
enum class RoundGlyph { PREVIOUS, NEXT, PLAY, PAUSE }

/** Geometry of the round-button artwork (`res/drawable-nodpi/round_key_*.webp`; see `tools/keyart/make_round.py`). */
private object RoundKeyArt {
    const val CANVAS = 240f      // the sprite is CANVAS x CANVAS px
    const val BODY_CY = 112f     // the body's centre, leaving room below for the shadow
    const val BODY_DIAMETER = 200f
    const val TRAVEL = 8f        // how far lower a pressed button sits
}

private val GlyphIdle = Color(0xFFE8DDC9)
private val GlyphLit = Color(0xFFFFE2A8)
private val GlyphGlow = Color(0xFFFFB347)
private val GlyphDimAmber = Color(0xFFC79A55)

/**
 * A round 1970s radio pushbutton from bitmap artwork, for PLAY and previous / next. [size] is the
 * button body's diameter; the artwork's canvas is a little larger (room for the shadow). Idle stands
 * proud; a finger on it (or [lit]) sinks it; [lit] also lights the amber ring — PLAY while playing.
 * The symbol is vector-drawn over the picture, so it stays crisp at any size.
 */
@Composable
fun RoundKey(
    glyph: RoundGlyph,
    size: Dp,
    lit: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lamp: Float = LocalLampBrightness.current,
) {
    val outArt = ImageBitmap.imageResource(R.drawable.round_key_out)
    val inArt = ImageBitmap.imageResource(R.drawable.round_key_in)
    val litArt = ImageBitmap.imageResource(R.drawable.round_key_lit)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed && enabled) 1f else 0f, tween(70), label = "round-press")
    val litLevel by animateFloatAsState(if (lit) 1f else 0f, tween(250), label = "round-lit")
    val down = maxOf(press, litLevel)

    val canvas = size * (RoundKeyArt.CANVAS / RoundKeyArt.BODY_DIAMETER)
    val scale = canvas.value / RoundKeyArt.CANVAS

    Box(
        modifier
            .size(canvas)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        // Pressed-in picture underneath, lit picture blended over it by the lamp brightness.
        Image(outArt, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = 1f - down, filterQuality = FilterQuality.High)
        Image(inArt, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = down, filterQuality = FilterQuality.High)
        Image(litArt, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = litLevel * lamp.coerceIn(0f, 1f), filterQuality = FilterQuality.High)

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = down * RoundKeyArt.TRAVEL * scale * density
                    alpha = if (enabled) 1f else 0.35f
                },
        ) {
            val cx = this.size.width / 2f
            val cy = this.size.height * (RoundKeyArt.BODY_CY / RoundKeyArt.CANVAS)
            val g = this.size.width * (RoundKeyArt.BODY_DIAMETER / RoundKeyArt.CANVAS) * 0.34f
            val path = glyphPath(glyph, cx, cy, g)
            if (litLevel > 0.5f) {
                // lit: an amber symbol with a soft glow
                drawPath(path, GlyphGlow.copy(alpha = 0.55f * lamp), style = Stroke(width = g * 0.38f, join = StrokeJoin.Round))
                drawPath(path, lerp(GlyphDimAmber, GlyphLit, lamp))
            } else {
                // engraved: a dark edge under the symbol
                val drop = 2f
                drawPath(glyphPath(glyph, cx, cy + drop, g), Color.Black)
                drawPath(path, GlyphIdle)
            }
        }
    }
}

/** The symbol's outline, centred on ([cx], [cy]) and about [g] px tall. */
private fun glyphPath(glyph: RoundGlyph, cx: Float, cy: Float, g: Float): Path = Path().apply {
    when (glyph) {
        RoundGlyph.PLAY -> {
            moveTo(cx - 0.30f * g, cy - 0.5f * g)
            lineTo(cx + 0.52f * g, cy)
            lineTo(cx - 0.30f * g, cy + 0.5f * g)
            close()
        }
        RoundGlyph.PAUSE -> {
            addRect(Rect(Offset(cx - 0.42f * g, cy - 0.5f * g), Size(0.30f * g, g)))
            addRect(Rect(Offset(cx + 0.12f * g, cy - 0.5f * g), Size(0.30f * g, g)))
        }
        RoundGlyph.PREVIOUS -> {
            addRect(Rect(Offset(cx - 0.52f * g, cy - 0.5f * g), Size(0.17f * g, g)))
            moveTo(cx + 0.52f * g, cy - 0.5f * g)
            lineTo(cx - 0.30f * g, cy)
            lineTo(cx + 0.52f * g, cy + 0.5f * g)
            close()
        }
        RoundGlyph.NEXT -> {
            addRect(Rect(Offset(cx + 0.35f * g, cy - 0.5f * g), Size(0.17f * g, g)))
            moveTo(cx - 0.52f * g, cy - 0.5f * g)
            lineTo(cx + 0.30f * g, cy)
            lineTo(cx - 0.52f * g, cy + 0.5f * g)
            close()
        }
    }
}
