package com.vintows.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = VintowsNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FA),
    onPrimaryContainer = VintowsNavyDark,
    secondary = VintowsBlue,
    onSecondary = Color.White,
    tertiary = VintowsAccent,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    outline = LightOutline,
    error = StatusRed,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = VintowsNavyDark,
    primaryContainer = VintowsNavy,
    onPrimaryContainer = Color(0xFFD6E4FA),
    secondary = Color(0xFF8AB8F0),
    tertiary = Color(0xFFF2A07A),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    outline = DarkOutline,
    error = Color(0xFFFF8A80),
)

private val VintowsTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

/** Brand colours are fixed (no dynamic colour) so the app always looks like Vintows. */
@Composable
fun VintowsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VintowsTypography,
        content = content,
    )
}
