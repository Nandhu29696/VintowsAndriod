package com.vintows.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vintows.app.BuildConfig
import com.vintows.app.core.designsystem.component.IconBadge
import com.vintows.app.core.designsystem.component.InitialsAvatar
import com.vintows.app.core.designsystem.component.SectionHeader
import com.vintows.app.core.designsystem.component.TagChip
import com.vintows.app.core.designsystem.component.VCard
import com.vintows.app.core.designsystem.theme.StatusRed
import com.vintows.app.core.designsystem.theme.VintowsAccent
import com.vintows.app.core.designsystem.theme.VintowsBlue
import com.vintows.app.core.designsystem.theme.VintowsNavy
import com.vintows.app.core.designsystem.theme.VintowsNavyDark
import com.vintows.app.core.session.Session
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What we know from the login response and JWT.
 * `auth/me` / `students/me` details come once the backend's profile endpoint works (500 "column dob" on QA).
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
    ) {
        Header(session)
        Spacer(Modifier.height(20.dp))

        SectionHeader("Account")
        Spacer(Modifier.height(8.dp))
        VCard(Modifier.fillMaxWidth()) {
            DetailRow(Icons.Outlined.Badge, "Role", session.role ?: "—")
            Divider()
            DetailRow(Icons.Outlined.Public, "Access", scopeLabel(session.scope))
            Divider()
            DetailRow(Icons.Outlined.Business, "Organisation", session.tenantId ?: "Vintows (global)")
            Divider()
            DetailRow(Icons.Outlined.Schedule, "Signed in until", session.expiresAtEpochSeconds?.let(::formatExpiry) ?: "—")
            Divider()
            DetailRow(Icons.Outlined.Fingerprint, "User ID", session.userId)
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = StatusRed)
            Text("  Log out", color = StatusRed)
        }
        if (BuildConfig.DEBUG) {
            TextButton(onClick = onOpenDiagnostics, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Outlined.BugReport, contentDescription = null)
                Text("  Diagnostics")
            }
        }
    }
}

@Composable
private fun Header(session: Session) {
    val name = displayNameOf(session.email)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(VintowsNavyDark, VintowsNavy, VintowsBlue)))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.border(3.dp, Color.White.copy(alpha = 0.35f), CircleShape).padding(4.dp)) {
            InitialsAvatar(name.ifBlank { session.email }, size = 76.dp, color = VintowsAccent)
        }
        Spacer(Modifier.height(12.dp))
        if (name.isNotBlank()) {
            Text(name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        Text(
            session.email,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        session.role?.let {
            Spacer(Modifier.height(10.dp))
            TagChip(it, Color.White)
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, VintowsBlue, size = 36.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

private fun scopeLabel(scope: String?): String = when (scope) {
    "admin" -> "Platform admin"
    "global" -> "Individual learner"
    "licensed" -> "Institution licence"
    null -> "—"
    else -> scope
}

private val expiryFormat = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)

private fun formatExpiry(epochSeconds: Long): String =
    expiryFormat.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))
