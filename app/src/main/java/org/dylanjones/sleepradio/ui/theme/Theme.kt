package org.dylanjones.sleepradio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Base Material 3 color scheme. SleepRadio is dark-only (both mockups are dark),
 * so there is no light scheme and no dynamic color.
 *
 * This is only the Material fallback. The per-skin palettes (Neon / Industrial)
 * are layered on top in Phase 2 via the AppSkin design system.
 */
private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonMagenta,
    tertiary = IndustrialAmber,
    background = BaseBackground,
    surface = BaseSurface,
)

@Composable
fun SleepRadioTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
