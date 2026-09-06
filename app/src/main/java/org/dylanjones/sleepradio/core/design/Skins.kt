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
