package com.example.persona.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF030712),
    primaryContainer = AccentDim,
    onPrimaryContainer = AccentLight,
    secondary = Purple,
    onSecondary = Color(0xFF030712),
    background = Color(0xFF030712),
    onBackground = Color(0xFFF0F0FF),
    surface = Color(0xFF0D1326),
    onSurface = Color(0xFFF0F0FF),
    surfaceVariant = Color(0x0FFFFFFF),
    onSurfaceVariant = Color(0xFFC8C8E0),
    outline = Color(0x14FFFFFF),
    error = Red,
    onError = Color(0xFF030712)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Purple,
    onSecondary = Color.White,
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xB3FFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0x29000000),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0x1F000000),
    error = Red,
    onError = Color.White
)

@Composable
fun PersonaTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (isDark) DarkPersonaColors else LightPersonaColors
    val scheme = if (isDark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalPersonaColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content
        )
    }
}
