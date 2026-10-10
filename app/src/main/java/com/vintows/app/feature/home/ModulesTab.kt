package com.vintows.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.vintows.app.core.designsystem.component.EmptyState
import com.vintows.app.core.designsystem.component.ErrorState
import com.vintows.app.core.designsystem.component.IconBadge
import com.vintows.app.core.designsystem.component.LoadingState
import com.vintows.app.core.designsystem.component.VCard
import com.vintows.app.core.designsystem.icon.bootstrapIcon
import com.vintows.app.core.designsystem.theme.VintowsBlue
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(Modifier.padding(bottom = 4.dp)) {
                        Text("Your modules", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Available to your role. Mobile screens for these arrive in later phases.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(menus.items, key = { it.id }) { ModuleCard(it) }
            }
        }
    }
}

@Composable
private fun ModuleCard(item: MenuItem) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val expandable = item.children.isNotEmpty()
    VCard(Modifier.fillMaxWidth(), onClick = if (expandable) ({ expanded = !expanded }) else null) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(bootstrapIcon(item.iconName), VintowsBlue)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                val subtitle = item.webRoute ?: item.key
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expandable) {
                Text(
                    "${item.children.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
            }
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            item.children.forEach { child ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(VintowsBlue, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(child.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
