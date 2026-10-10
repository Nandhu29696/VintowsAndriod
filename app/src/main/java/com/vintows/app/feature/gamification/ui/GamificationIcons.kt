package com.vintows.app.feature.gamification.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Badge and mission icons arrive as Bootstrap icon names (`bi-trophy`, `bi-flag-fill`).
 * Matches on the base name, so `-fill` variants and new names like `bi-trophy-2` still map.
 */
internal fun bootstrapIcon(name: String?): ImageVector {
    val base = name.orEmpty().lowercase().removePrefix("bi-").removeSuffix("-fill")
    return ICONS.entries.firstOrNull { base.startsWith(it.key) }?.value ?: Icons.Outlined.WorkspacePremium
}

private val ICONS: Map<String, ImageVector> = linkedMapOf(
    "flag" to Icons.Outlined.Flag,
    "mortarboard" to Icons.Outlined.School,
    "lightning" to Icons.Outlined.Bolt,
    "trophy" to Icons.Outlined.EmojiEvents,
    "award" to Icons.Outlined.WorkspacePremium,
    "star" to Icons.Outlined.Star,
    "fire" to Icons.Outlined.LocalFireDepartment,
    "book" to Icons.AutoMirrored.Outlined.MenuBook,
    "journal" to Icons.AutoMirrored.Outlined.MenuBook,
    "check" to Icons.Outlined.CheckCircle,
    "code" to Icons.Outlined.Code,
    "people" to Icons.Outlined.Groups,
    "calendar" to Icons.Outlined.CalendarMonth,
    "gem" to Icons.Outlined.Diamond,
    "bullseye" to Icons.Outlined.TrackChanges,
)
