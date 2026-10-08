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
    // Neutral blue-grey containers; Material's defaults are lavender, which clashes with the navy brand.
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color(0xFFF2F5F9),
    surfaceContainerHigh = Color(0xFFEDF1F6),
    surfaceContainerHighest = Color(0xFFE7ECF2),
    secondaryContainer = Color(0xFFD6E4FA),
    onSecondaryContainer = VintowsNavyDark,
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
    surfaceContainerLowest = Color(0xFF0A1017),
    surfaceContainerLow = Color(0xFF121A24),
    surfaceContainer = Color(0xFF17202C),
    surfaceContainerHigh = Color(0xFF1C2633),
    surfaceContainerHighest = Color(0xFF232E3C),
    secondaryContainer = VintowsNavy,
    onSecondaryContainer = Color(0xFFD6E4FA),
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
