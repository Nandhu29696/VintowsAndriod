package com.vintows.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vintows.app.R

private val LightColors = lightColorScheme(
    primary = VintowsNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FB),
    onPrimaryContainer = VintowsNavyDark,
    secondary = VintowsBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8FB),
    onSecondaryContainer = VintowsNavyDark,
    tertiary = VintowsAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFDE6DB),
    onTertiaryContainer = Color(0xFF6B2509),
    background = LightBackground,
    onBackground = Ink,
    surface = LightSurface,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
    surfaceVariant = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightHairline,
    error = StatusRed,
    // Neutral blue-grey containers; Material's defaults are lavender, which clashes with the navy brand.
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color(0xFFF3F6FA),
    surfaceContainerHigh = Color(0xFFEEF2F7),
    surfaceContainerHighest = Color(0xFFE8EDF3),
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = VintowsNavyDark,
    primaryContainer = Color(0xFF173B66),
    onPrimaryContainer = Color(0xFFD6E4FA),
    secondary = Color(0xFF8AB8F0),
    secondaryContainer = Color(0xFF173B66),
    onSecondaryContainer = Color(0xFFD6E4FA),
    tertiary = Color(0xFFF2A07A),
    tertiaryContainer = Color(0xFF4A2412),
    onTertiaryContainer = Color(0xFFFDE6DB),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF2A3644),
    error = Color(0xFFFF8A80),
    surfaceContainerLowest = Color(0xFF0A1017),
    surfaceContainerLow = Color(0xFF121A24),
    surfaceContainer = Color(0xFF17202C),
    surfaceContainerHigh = Color(0xFF1C2633),
    surfaceContainerHighest = Color(0xFF232E3C),
)

/** Plus Jakarta Sans (SIL OFL 1.1, see assets/licenses). One variable font file covers every weight. */
private fun jakarta(weight: FontWeight) = Font(
    R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val JakartaSans = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold),
)

private val VintowsTypography = Typography().run {
    fun TextStyle.jakarta(weight: FontWeight? = null, tracking: Float? = null) = copy(
        fontFamily = JakartaSans,
        fontWeight = weight ?: fontWeight,
        letterSpacing = tracking?.sp ?: letterSpacing,
    )
    copy(
        displaySmall = displaySmall.jakarta(FontWeight.ExtraBold, -0.5f),
        headlineLarge = headlineLarge.jakarta(FontWeight.ExtraBold, -0.5f),
        headlineMedium = headlineMedium.jakarta(FontWeight.Bold, -0.3f),
        headlineSmall = headlineSmall.jakarta(FontWeight.Bold, -0.2f),
        titleLarge = titleLarge.jakarta(FontWeight.Bold, -0.2f),
        titleMedium = titleMedium.jakarta(FontWeight.Bold, 0f),
        titleSmall = titleSmall.jakarta(FontWeight.SemiBold, 0f),
        bodyLarge = bodyLarge.jakarta(tracking = 0f),
        bodyMedium = bodyMedium.jakarta(tracking = 0f),
        bodySmall = bodySmall.jakarta(tracking = 0.1f),
        labelLarge = labelLarge.jakarta(FontWeight.SemiBold),
        labelMedium = labelMedium.jakarta(FontWeight.SemiBold),
        labelSmall = TextStyle(fontFamily = JakartaSans, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    )
}

private val VintowsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Brand colours are fixed (no dynamic colour) so the app always looks like Vintows. */
@Composable
fun VintowsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VintowsTypography,
        shapes = VintowsShapes,
        content = content,
    )
}
