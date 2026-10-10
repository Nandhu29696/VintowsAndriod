package com.vintows.app.feature.notifications.ui

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
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.DetailTopBar
import com.vintows.app.core.designsystem.component.EmptyState
import com.vintows.app.core.designsystem.component.VCard
import com.vintows.app.core.designsystem.component.ErrorState
import com.vintows.app.core.designsystem.component.LoadingState
import com.vintows.app.core.util.DateFormatter
import com.vintows.app.feature.notifications.data.AppNotification

@Composable
fun NotificationsRoute(
    onBack: () -> Unit,
    pushEnabled: Boolean,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationsScreen(
        state = state,
        pushEnabled = pushEnabled,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onTransientErrorShown = viewModel::consumeTransientError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    pushEnabled: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onTransientErrorShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.transientError) {
        state.transientError?.let {
            snackbar.showSnackbar(it)
            onTransientErrorShown()
        }
    }

    Scaffold(
        topBar = {
            DetailTopBar(title = "Notifications", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error, onRetry = onRetry)
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                    if (state.items.isEmpty()) {
                        // Wrapped in a LazyColumn so pull-to-refresh still works on the empty state.
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                EmptyState(
                                    title = "No notifications yet",
                                    message = if (pushEnabled) {
                                        "Course updates and test reminders will appear here."
                                    } else {
                                        "Course updates will appear here. Push alerts are not enabled in this build yet."
                                    },
                                    icon = Icons.Outlined.NotificationsNone,
                                    modifier = Modifier.padding(top = 96.dp),
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.items, key = { it.id }) { NotificationCard(it) }
                            if (state.hasNextPage) {
                                item {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        if (state.loadingMore) {
                                            CircularProgressIndicator(Modifier.size(24.dp))
                                        } else {
                                            TextButton(onClick = onLoadMore) { Text("Load more") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: AppNotification) {
    VCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            if (!item.read) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold)
                if (item.body.isNotBlank()) {
                    Text(item.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val time = DateFormatter.dateTime(item.createdAt)
                if (time.isNotEmpty()) {
                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
