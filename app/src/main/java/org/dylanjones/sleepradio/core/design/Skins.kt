package org.dylanjones.sleepradio.core.design

import androidx.compose.ui.graphics.Color

/** Cyberpunk / neon skin — mockup 1. Cyan + magenta on deep indigo, frosted tiles. */
object NeonSkin : AppSkin {
    override val id = SkinId.NEON
    override val displayName = "Neon"
    override val colors = SkinColors(
        screenTop = Color(0xFF0B0B1A),
        screenBottom = Color(0xFF15173A),
        panelTop = Color(0xFF2B3040),
        panelBottom = Color(0xFF181B26),
        panelStroke = Color(0xFF3D4250),
        accent = Color(0xFF33E1FF),
        accentAlt = Color(0xFFFF3DA6),
        glow = Color(0xFF33E1FF),
        tileFace = Color(0xFF9FB6CC),
        tileFaceActive = Color(0xFFC7DCEC),
        tileStroke = Color(0xFF33E1FF),
        onTile = Color(0xFF0A1020),
        textPrimary = Color(0xFFEAF6FF),
        textSecondary = Color(0xFF9FB2C6),
        textDim = Color(0xFF62708A),
    )
}

/**
 * Warm hi-fi console skin. Amber-on-espresso, cream tiles; [PlayerLayout.CONSOLE]
 * — one control row (RWD · VOL · PAUSE · BAL · FFWD), half-height SLEEP/NOISE,
 * and a pair of analogue L/R VU meters where the FFT strip would be.
 */
object StudioSkin : AppSkin {
    override val id = SkinId.STUDIO
    override val displayName = "Studio"
    override val layout = PlayerLayout.CONSOLE
    override val colors = SkinColors(
        screenTop = Color(0xFF15120E),
        screenBottom = Color(0xFF221C14),
        panelTop = Color(0xFF3A322A),
        panelBottom = Color(0xFF241E18),
        panelStroke = Color(0xFF4E443A),
        accent = Color(0xFFE0A64C),
        accentAlt = Color(0xFFC24B3A),
        glow = Color(0xFFE0A64C),
        tileFace = Color(0xFF2C2620),
        tileFaceActive = Color(0xFF473A2B),
        tileStroke = Color(0xFF5C5044),
        onTile = Color(0xFFEDE3D3),
        textPrimary = Color(0xFFF2E9D9),
        textSecondary = Color(0xFFB9AC97),
        textDim = Color(0xFF7C7060),
    )
}

/** Brushed-metal / industrial skin — mockup 2. Blue + amber on near-black steel. */
object IndustrialSkin : AppSkin {
    override val id = SkinId.INDUSTRIAL
    override val displayName = "Industrial"
    override val colors = SkinColors(
        screenTop = Color(0xFF0A0A0C),
        screenBottom = Color(0xFF17181C),
        panelTop = Color(0xFF3A3D42),
        panelBottom = Color(0xFF232529),
        panelStroke = Color(0xFF4A4E55),
        accent = Color(0xFF5AA9E6),
        accentAlt = Color(0xFFE8912D),
        glow = Color(0xFF5AA9E6),
        tileFace = Color(0xFF2E3033),
        tileFaceActive = Color(0xFF3C4750),
        tileStroke = Color(0xFF5A6068),
        onTile = Color(0xFFD8DDE2),
        textPrimary = Color(0xFFE8ECEF),
        textSecondary = Color(0xFFAAB1B8),
        textDim = Color(0xFF6C7278),
    )
}
