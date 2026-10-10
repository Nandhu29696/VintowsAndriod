package com.vintows.app.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The backend sends Bootstrap icon names (`bi-trophy`, `bi-flag-fill`, `bi-gear-wide`) for menus, badges,
 * missions and content types. Matches on the base name, so `-fill` variants and new names like
 * `bi-trophy-2` still map; anything unknown gets [fallback].
 */
fun bootstrapIcon(name: String?, fallback: ImageVector = Icons.Outlined.Folder): ImageVector {
    val base = name.orEmpty().lowercase().removePrefix("bi-").removeSuffix("-fill")
    if (base.isEmpty()) return fallback
    return ICONS.entries.firstOrNull { base.startsWith(it.key) }?.value ?: fallback
}

private val ICONS: Map<String, ImageVector> = linkedMapOf(
    // gamification
    "flag" to Icons.Outlined.Flag,
    "mortarboard" to Icons.Outlined.School,
    "lightning" to Icons.Outlined.Bolt,
    "trophy" to Icons.Outlined.EmojiEvents,
    "award" to Icons.Outlined.EmojiEvents,
    "star" to Icons.Outlined.Star,
    "fire" to Icons.Outlined.LocalFireDepartment,
    "check" to Icons.Outlined.CheckCircle,
    "people" to Icons.Outlined.Groups,
    "calendar" to Icons.Outlined.CalendarMonth,
    "gem" to Icons.Outlined.Diamond,
    "bullseye" to Icons.Outlined.TrackChanges,
    // course content
    "broadcast" to Icons.Outlined.Sensors,
    "file-earmark" to Icons.Outlined.Description,
    "code" to Icons.Outlined.Code,
    "easel" to Icons.Outlined.Draw,
    "link" to Icons.Outlined.Link,
    "book" to Icons.AutoMirrored.Outlined.MenuBook,
    "journal" to Icons.AutoMirrored.Outlined.MenuBook,
    // admin menus
    "layout" to Icons.AutoMirrored.Outlined.ViewQuilt,
    "toggles" to Icons.Outlined.ToggleOn,
    "gear" to Icons.Outlined.Settings,
    "pencil" to Icons.Outlined.Edit,
    "table" to Icons.Outlined.TableChart,
    "bar-chart" to Icons.Outlined.Assessment,
    "graph" to Icons.Outlined.Assessment,
)
