package com.vintows.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vintows.app.core.designsystem.component.EmptyState
import com.vintows.app.core.designsystem.component.ErrorState
import com.vintows.app.core.designsystem.component.LoadingState
import com.vintows.app.core.rbac.MenuItem

/** The role's modules from `roleaccess/get`, same tree as the web menu bar. Read-only for now. */
@Composable
fun ModulesTab(menus: MenusState, onRetry: () -> Unit) {
    when (menus) {
        MenusState.Loading -> LoadingState()
        is MenusState.Error -> ErrorState(menus.message, onRetry = onRetry)
        is MenusState.Loaded -> if (menus.items.isEmpty()) {
            EmptyState("No modules", "Your role has no modules assigned yet.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Modules available to your role. Mobile screens for these arrive in later phases.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(menus.items, key = { it.id }) { ModuleCard(it) }
            }
        }
    }
}

@Composable
private fun ModuleCard(item: MenuItem) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = item.children.isNotEmpty()) { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(bootstrapIcon(item.iconName), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                val subtitle = item.webRoute ?: item.key
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (item.children.isNotEmpty()) {
                Text("${item.children.size}", style = MaterialTheme.typography.labelMedium)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
        }
        if (expanded) {
            item.children.forEach { child ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("• ${child.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Maps the server's Bootstrap icon names to Material icons; unknown names get a folder. */
internal fun bootstrapIcon(name: String?): ImageVector = when (name) {
    "bi-star-fill" -> Icons.Outlined.Star
    "bi-layout-wtf" -> Icons.AutoMirrored.Outlined.ViewQuilt
    "bi-toggles" -> Icons.Outlined.ToggleOn
    "bi-gear-wide", "bi-gear" -> Icons.Outlined.Settings
    "bi-pencil-fill" -> Icons.Outlined.Edit
    "bi-table" -> Icons.Outlined.TableChart
    "bi-bar-chart", "bi-graph-up" -> Icons.Outlined.Assessment
    else -> Icons.Outlined.Folder
}
