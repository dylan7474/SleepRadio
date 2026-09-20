package org.dylanjones.sleepradio.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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

/**
 * The app's one look: the warm "Studio" console (see [StudioSkin]). The Neon and Industrial
 * skins and the appearance setting were removed so every source type shares a single,
 * fixed screen. [AppSkin] stays as the token holder that composables read through
 * [LocalAppSkin].
 */
@Immutable
interface AppSkin {
    val colors: SkinColors
}

fun SkinColors.screenBrush(): Brush = Brush.verticalGradient(listOf(screenTop, screenBottom))

fun SkinColors.panelBrush(): Brush = Brush.verticalGradient(listOf(panelTop, panelBottom))

fun SkinColors.tileBrush(active: Boolean): Brush =
    if (active) Brush.verticalGradient(listOf(tileFaceActive, tileFace))
    else Brush.verticalGradient(listOf(tileFace, tileFace.copy(alpha = 0.72f)))

val LocalAppSkin = staticCompositionLocalOf<AppSkin> { StudioSkin }
