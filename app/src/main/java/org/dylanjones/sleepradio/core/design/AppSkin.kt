package org.dylanjones.sleepradio.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The selectable visual skins. See RETROSYNC_PLAN.md section 3. Neon and
 * Industrial share one player layout and differ only in colour / texture;
 * Studio ([PlayerLayout.CONSOLE]) rearranges the lower half. [AppSkin] carries
 * the shared token vocabulary they all draw from.
 */
enum class SkinId { NEON, INDUSTRIAL, STUDIO }

/**
 * Which player layout a skin uses for the lower half of the screen.
 *  - [CLASSIC]  transport cluster over a VOL/BAL knob row; FFT visualiser strip.
 *  - [CONSOLE]  one row — RWD · VOL · PAUSE · BAL · FFWD; half-height SLEEP/NOISE
 *    tiles; analogue L/R VU meters in the reclaimed space; no visualiser strip.
 */
enum class PlayerLayout { CLASSIC, CONSOLE }

/** Semantic colour tokens for a skin. */
@Immutable
data class SkinColors(
    val screenTop: Color,
    val screenBottom: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val panelStroke: Color,
    val accent: Color,
    val accentAlt: Color,
    val glow: Color,
    val tileFace: Color,
    val tileFaceActive: Color,
    val tileStroke: Color,
    val onTile: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDim: Color,
)

@Immutable
interface AppSkin {
    val id: SkinId
    val displayName: String
    val colors: SkinColors
    val layout: PlayerLayout get() = PlayerLayout.CLASSIC
}

fun SkinColors.screenBrush(): Brush = Brush.verticalGradient(listOf(screenTop, screenBottom))

fun SkinColors.panelBrush(): Brush = Brush.verticalGradient(listOf(panelTop, panelBottom))

fun SkinColors.tileBrush(active: Boolean): Brush =
    if (active) Brush.verticalGradient(listOf(tileFaceActive, tileFace))
    else Brush.verticalGradient(listOf(tileFace, tileFace.copy(alpha = 0.72f)))

val LocalAppSkin = staticCompositionLocalOf<AppSkin> { NeonSkin }

fun skinFor(id: SkinId): AppSkin = when (id) {
    SkinId.NEON -> NeonSkin
    SkinId.INDUSTRIAL -> IndustrialSkin
    SkinId.STUDIO -> StudioSkin
}
