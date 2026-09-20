package org.dylanjones.sleepradio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.dylanjones.sleepradio.core.design.StudioSkin

/**
 * The Material 3 colour scheme, built from the Studio skin so dialogs, switches, buttons,
 * sliders and the drawer match the player screen (amber on espresso) instead of falling back
 * to Material's default purple/grey. SleepRadio is dark-only, so there is no light scheme and
 * no dynamic colour.
 */
private val StudioColorScheme = StudioSkin.colors.let { c ->
    darkColorScheme(
        // Amber accent: buttons, links, switch tracks, sliders, selected radios.
        primary = c.accent,
        onPrimary = c.screenTop,
        primaryContainer = c.tileFaceActive,
        onPrimaryContainer = c.textPrimary,
        inversePrimary = Color(0xFF8A5F1C),
        // Warm neutral: the selected item in the drawer sits on secondaryContainer.
        secondary = c.textSecondary,
        onSecondary = c.screenTop,
        secondaryContainer = c.panelTop,
        onSecondaryContainer = c.textPrimary,
        tertiary = c.accentAlt,
        onTertiary = c.textPrimary,
        tertiaryContainer = Color(0xFF4A2A24),
        onTertiaryContainer = c.textPrimary,
        background = c.screenTop,
        onBackground = c.textPrimary,
        surface = StudioSurface,
        onSurface = c.textPrimary,
        surfaceVariant = c.panelTop,
        onSurfaceVariant = c.textSecondary,
        surfaceTint = c.accent,
        inverseSurface = c.textPrimary,
        inverseOnSurface = c.screenTop,
        outline = c.textDim,
        outlineVariant = c.panelStroke,
        surfaceDim = StudioSurfaceLowest,
        surfaceBright = StudioSurfaceHighest,
        surfaceContainerLowest = StudioSurfaceLowest,
        surfaceContainerLow = StudioSurfaceLow,
        surfaceContainer = c.panelBottom,
        surfaceContainerHigh = StudioSurfaceHigh,
        surfaceContainerHighest = StudioSurfaceHighest,
    )
}

@Composable
fun SleepRadioTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = Typography,
        content = content,
    )
}
