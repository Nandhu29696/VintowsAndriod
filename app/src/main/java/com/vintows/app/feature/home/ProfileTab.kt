package com.vintows.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vintows.app.BuildConfig
import com.vintows.app.core.designsystem.component.InitialsAvatar
import com.vintows.app.core.session.Session
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Phase 1 profile: what we know from the login response and JWT.
 * Phase 3 adds `auth/me` / `students/me` details (auth/me currently returns 500 on QA).
 */
@Composable
fun ProfileTab(
    session: Session,
    onLogout: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InitialsAvatar(session.email, size = 72.dp)
        Spacer(Modifier.height(12.dp))
        Text(session.email, style = MaterialTheme.typography.titleMedium)
        session.role?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(20.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("User ID", session.userId.toString())
                DetailRow("Role ID", session.roleId?.toString() ?: "—")
                DetailRow("Scope", session.scope ?: "—")
                DetailRow("Tenant", session.tenantId ?: "—")
                DetailRow("Session expires", session.expiresAtEpochSeconds?.let(::formatExpiry) ?: "—")
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            Text("  Log out")
        }
        if (BuildConfig.DEBUG) {
            TextButton(onClick = onOpenDiagnostics) {
                Icon(Icons.Outlined.BugReport, contentDescription = null)
                Text("  Diagnostics")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val expiryFormat = DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm a", Locale.ENGLISH)

private fun formatExpiry(epochSeconds: Long): String =
    expiryFormat.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))
