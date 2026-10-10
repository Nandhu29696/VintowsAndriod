package com.vintows.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Brand (web: theme-color #033266, login gradient #1A73D9 → #032A5C)
val VintowsNavy = Color(0xFF033266)
val VintowsNavyDark = Color(0xFF021F40)
val VintowsBlue = Color(0xFF1A73D9)
val VintowsAccent = Color(0xFFE2632B) // orange used on course cards

val LightBackground = Color(0xFFF4F7FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEF2F8)
val LightOutline = Color(0xFFD5DCE6)
/** Card borders and dividers: lighter than [LightOutline]. */
val LightHairline = Color(0xFFE4E9F0)

// Text
val Ink = Color(0xFF0F1B2D)
val InkMuted = Color(0xFF5B6B80)

val DarkBackground = Color(0xFF0E141C)
val DarkSurface = Color(0xFF151D28)
val DarkSurfaceVariant = Color(0xFF1F2A38)
val DarkOutline = Color(0xFF3A4757)
val DarkPrimary = Color(0xFF9CC3FF)

// Semantic colours for status / priority chips
val StatusBlue = Color(0xFF1A73D9)
val StatusAmber = Color(0xFFD98A00)
val StatusGreen = Color(0xFF1E8E5A)
val StatusGrey = Color(0xFF6B7785)
val StatusPurple = Color(0xFF7B4FD6)
val StatusRed = Color(0xFFD93A3A)

/**
 * Course cover gradients. A course always gets the same pair (picked from its name),
 * so covers look varied but stable between visits.
 */
val CoverGradients = listOf(
    Color(0xFF1A73D9) to Color(0xFF033266),
    Color(0xFFE2632B) to Color(0xFFB2361B),
    Color(0xFF16A085) to Color(0xFF0B5E52),
    Color(0xFF7B4FD6) to Color(0xFF3F2391),
    Color(0xFFD98A00) to Color(0xFF9A4A00),
    Color(0xFF2E86C1) to Color(0xFF14496E),
    Color(0xFFD6457A) to Color(0xFF8A1E48),
)

fun coverGradientFor(key: String): Pair<Color, Color> = CoverGradients[Math.floorMod(key.hashCode(), CoverGradients.size)]
