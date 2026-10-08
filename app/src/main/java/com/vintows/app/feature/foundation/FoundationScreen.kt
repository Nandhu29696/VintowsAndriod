package com.vintows.app.feature.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.InitialsAvatar
import com.vintows.app.core.designsystem.component.PriorityChip
import com.vintows.app.core.designsystem.component.StatusChip
import com.vintows.app.core.designsystem.theme.StatusGreen
import com.vintows.app.core.designsystem.theme.StatusRed
import com.vintows.app.core.designsystem.theme.VintowsTheme

@Composable
fun FoundationRoute(onBack: () -> Unit, viewModel: FoundationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FoundationScreen(state = state, onCheckApi = viewModel::checkApi, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundationScreen(state: FoundationUiState, onCheckApi: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("Environment") {
                InfoRow("Flavour", state.environment)
                InfoRow("API", state.baseUrl)
                InfoRow("Version", state.version)
                InfoRow("Session", state.sessionSummary)
            }

            SectionCard("API connection") {
                ApiCheckResult(state.apiCheck)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onCheckApi, enabled = state.apiCheck !is ApiCheck.Running) {
                    Text("Check API")
                }
            }

            SectionCard("Design system") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Open", "InProgress", "Resolved", "Closed", "PendingInstitutionReview").forEach { StatusChip(it) }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Low", "Medium", "High", "Critical").forEach { PriorityChip(it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InitialsAvatar("dev.team@example.com")
                    InitialsAvatar("Admin")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ApiCheckResult(check: ApiCheck) {
    when (check) {
        ApiCheck.Idle -> Text("Not checked yet", style = MaterialTheme.typography.bodyMedium)
        ApiCheck.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("  Contacting server…")
        }
        is ApiCheck.Reachable -> Text("✓ Reachable: ${check.detail}", color = StatusGreen)
        is ApiCheck.Unreachable -> Text("✗ ${check.detail}", color = StatusRed)
    }
}

@Preview(showBackground = true)
@Composable
private fun FoundationScreenPreview() {
    VintowsTheme {
        FoundationScreen(
            state = FoundationUiState(
                sessionSummary = "Not logged in",
                apiCheck = ApiCheck.Reachable("HTTP 401 · \"No authentication token provided\""),
            ),
            onCheckApi = {},
            onBack = {},
        )
    }
}
