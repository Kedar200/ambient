package com.ambient.os.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AmbientColors {
    val BackgroundDeep = Color(0xFF05070D)
    val BackgroundSurface = Color(0xFF0C111C)
    val AccentCyan = Color(0xFF5FE3FF)
    val AccentViolet = Color(0xFF8A6BFF)
    val AccentMagenta = Color(0xFFFF4DD2)
    val Danger = Color(0xFFFF4D6D)
    val Success = Color(0xFF4DFFB6)
    val TextPrimary = Color(0xFFE8F4FF)
    val TextSecondary = Color(0xFF8AA0B8)
    val GridLine = Color(0x335FE3FF)
}

private val AmbientDarkColors = darkColorScheme(
    primary = AmbientColors.AccentCyan,
    secondary = AmbientColors.AccentViolet,
    tertiary = AmbientColors.AccentMagenta,
    background = AmbientColors.BackgroundDeep,
    surface = AmbientColors.BackgroundSurface,
    onPrimary = AmbientColors.BackgroundDeep,
    onBackground = AmbientColors.TextPrimary,
    onSurface = AmbientColors.TextPrimary,
    error = AmbientColors.Danger,
)

private val AmbientTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 40.sp,
        letterSpacing = 2.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        letterSpacing = 1.5.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
    ),
)

@Composable
fun AmbientTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val isDark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = AmbientDarkColors,
        typography = AmbientTypography,
        content = content,
    )
}
