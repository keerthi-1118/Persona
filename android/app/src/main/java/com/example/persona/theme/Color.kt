package com.example.persona.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class PersonaColors(
    val bg: Color,
    val bgElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderLight: Color,
    val glass: Color,
    val glass2: Color,
    val dialogBg: Color
)

val DarkPersonaColors = PersonaColors(
    bg = Color(0xFF030712),
    bgElevated = Color(0x14FFFFFF), // 8% translucent white glass
    textPrimary = Color(0xFFF0F0FF),
    textSecondary = Color(0xFFC8C8E0),
    textMuted = Color(0xFF7878A0),
    border = Color(0x3DFFFFFF),     // 24% reflective white highlight
    borderLight = Color(0x52FFFFFF),// 32% reflective white highlight
    glass = Color(0x08FFFFFF),
    glass2 = Color(0x0FFFFFFF),
    dialogBg = Color(0xE0050816)     // 88% translucent deep space glass
)

val LightPersonaColors = PersonaColors(
    bg = Color(0xFFF3F4F6),
    bgElevated = Color(0x66FFFFFF), // 40% translucent white glass
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8),
    border = Color(0x2BFFFFFF),     // 17% reflective white highlight
    borderLight = Color(0x4DFFFFFF),// 30% reflective white highlight
    glass = Color(0x14000000),
    glass2 = Color(0x29000000),
    dialogBg = Color(0xE6FFFFFF)     // 90% translucent white glass
)

val LocalPersonaColors = staticCompositionLocalOf { DarkPersonaColors }

val Bg: Color
    @Composable get() = LocalPersonaColors.current.bg

val BgElevated: Color
    @Composable get() = LocalPersonaColors.current.bgElevated

val DialogBg: Color
    @Composable get() = LocalPersonaColors.current.dialogBg

val Accent     = Color(0xFF38BDF8)   // Sky blue — all buttons, borders, active icons
val AccentLight = Color(0xFF7DD3FC)  // Lighter sky blue — hover/highlight states
val AccentDim   = Color(0x2238BDF8)  // Transparent sky blue — subtle backgrounds

val Green = Color(0xFF10B981)
val Amber = Color(0xFFF59E0B)
val Red = Color(0xFFF43F5E)
val Pink = Color(0xFFEC4899)
val Teal = Color(0xFF14B8A6)
val Purple = Color(0xFFA855F7)

val TextPrimary: Color
    @Composable get() = LocalPersonaColors.current.textPrimary

val TextSecondary: Color
    @Composable get() = LocalPersonaColors.current.textSecondary

val TextMuted: Color
    @Composable get() = LocalPersonaColors.current.textMuted

val Border: Color
    @Composable get() = LocalPersonaColors.current.border

val BorderLight: Color
    @Composable get() = LocalPersonaColors.current.borderLight

val Glass: Color
    @Composable get() = LocalPersonaColors.current.glass

val Glass2: Color
    @Composable get() = LocalPersonaColors.current.glass2
