package com.vintows.app.feature.courses.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.DetailTopBar
import com.vintows.app.core.designsystem.component.EmptyState
import com.vintows.app.core.designsystem.component.ErrorState
import com.vintows.app.core.designsystem.component.IconBadge
import com.vintows.app.core.designsystem.component.LoadingState
import com.vintows.app.core.designsystem.component.PriceTag
import com.vintows.app.core.designsystem.component.SectionHeader
import com.vintows.app.core.designsystem.component.TagChip
import com.vintows.app.core.designsystem.component.VCard
import com.vintows.app.core.designsystem.icon.bootstrapIcon
import com.vintows.app.core.designsystem.theme.StatusAmber
import com.vintows.app.core.designsystem.theme.StatusBlue
import com.vintows.app.core.designsystem.theme.StatusGreen
import com.vintows.app.core.designsystem.theme.StatusGrey
import com.vintows.app.core.designsystem.theme.StatusPurple
import com.vintows.app.core.designsystem.theme.StatusRed
import com.vintows.app.core.designsystem.theme.VintowsAccent
import com.vintows.app.core.designsystem.theme.coverGradientFor
import com.vintows.app.core.ui.Section
import com.vintows.app.feature.assessments.domain.Assessment
import com.vintows.app.feature.assessments.ui.TestCard
import com.vintows.app.feature.courses.domain.ContentItem
import com.vintows.app.feature.courses.domain.ContentType
import com.vintows.app.feature.courses.domain.CourseNode
import com.vintows.app.feature.courses.domain.NodeContents

@Composable
fun CourseNodeRoute(
    onBack: () -> Unit,
    onOpenNode: (CourseNodeDestination) -> Unit,
    onOpenContent: (ContentDestination) -> Unit,
    onOpenTest: (Assessment) -> Unit,
    viewModel: CourseNodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CourseNodeScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onOpenChild = { child ->
            onOpenNode(
                CourseNodeDestination(
                    programId = state.programId,
                    levelCode = child.levelCode,
                    nodeId = child.id,
                    title = child.name,
                    trail = listOf(state.trail, state.title).filter { it.isNotBlank() }.joinToString(" › "),
                    contentUrl = child.contentUrl,
                ),
            )
        },
        onOpenContent = { item ->
            onOpenContent(ContentDestination(item.type.apiPath, item.id, state.levelCode, state.nodeId, item.name))
        },
        onOpenTest = onOpenTest,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseNodeScreen(
    state: CourseNodeUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenChild: (CourseNode) -> Unit,
    onOpenContent: (ContentItem) -> Unit,
    onOpenTest: (Assessment) -> Unit,
) {
    Scaffold(topBar = { DetailTopBar(title = state.title, subtitle = state.trail, onBack = onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val contents = state.contents) {
                Section.Loading -> LoadingState()
                is Section.Failed -> ErrorState(contents.message, onRetry = onRetry)
                is Section.Loaded -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    NodeBody(state, contents.data, onOpenChild, onOpenContent, onOpenTest)
                }
            }
        }
    }
}

@Composable
private fun NodeBody(
    state: CourseNodeUiState,
    data: NodeContents,
    onOpenChild: (CourseNode) -> Unit,
    onOpenContent: (ContentItem) -> Unit,
    onOpenTest: (Assessment) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Hero(state, data) }
        // Topics can carry their own external material link (topics.content_url).
        state.contentUrl?.let { url -> item { OpenLinkButton(context, url) } }

        if (data.materials.isNotEmpty()) {
            item { SectionHeader("Learning materials", trailing = "${data.materials.size}", modifier = Modifier.padding(top = 14.dp)) }
            itemsIndexed(data.materials, key = { _, it -> "m-${it.type}-${it.id}" }) { _, item ->
                MaterialRow(item, onClick = { onOpenContent(item) })
            }
        }

        if (state.tests.isNotEmpty()) {
            item { SectionHeader("Tests", trailing = "${state.tests.size}", modifier = Modifier.padding(top = 14.dp)) }
            itemsIndexed(state.tests, key = { _, it -> "t-${it.id}" }) { _, test ->
                TestCard(test, onClick = { onOpenTest(test) })
            }
        }

        val childLevel = data.childLevel
        if (childLevel != null && data.children.isNotEmpty()) {
            item {
                SectionHeader(pluralLabel(childLevel.label), trailing = "${data.children.size}", modifier = Modifier.padding(top = 14.dp))
            }
            itemsIndexed(data.children, key = { _, it -> "c-${it.id}" }) { index, child ->
                ChildRow(
                    index = index + 1,
                    node = child,
                    accent = coverGradientFor(state.programId).first,
                    onClick = { onOpenChild(child) },
                )
            }
        }


        if (data.materials.isEmpty() && data.children.isEmpty() && state.tests.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing here yet",
                    message = if (childLevel == null) {
                        "Materials for this ${levelName(state.levelCode)} haven't been published yet."
                    } else {
                        "No ${pluralLabel(childLevel.label).lowercase()} or materials have been published yet."
                    },
                    icon = Icons.Outlined.Inventory2,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun Hero(state: CourseNodeUiState, data: NodeContents) {
    val (start, end) = coverGradientFor(state.programId)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(start, end)))
            .padding(20.dp),
    ) {
        TagChip(levelName(state.levelCode).replaceFirstChar { it.uppercase() }, Color.White)
        Spacer(Modifier.height(10.dp))
        Text(state.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
        val stats = buildList {
            data.childLevel?.let { level ->
                if (data.children.isNotEmpty()) add("${data.children.size} ${if (data.children.size == 1) level.label.lowercase() else pluralLabel(level.label).lowercase()}")
            }
            if (data.materials.isNotEmpty()) add("${data.materials.size} material${if (data.materials.size == 1) "" else "s"}")
        }
        if (stats.isNotEmpty()) {
            Text(
                stats.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ChildRow(index: Int, node: CourseNode, accent: Color, onClick: () -> Unit) {
    VCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$index", style = MaterialTheme.typography.titleSmall, color = accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(node.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                node.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (node.duration != null || node.isPaid) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        node.duration?.let {
                            Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = StatusGrey, modifier = Modifier.size(14.dp))
                            Text(" $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (node.isPaid) PriceTag(node.isPaid, node.price)
                    }
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MaterialRow(item: ContentItem, onClick: () -> Unit) {
    val tint = item.type.tint()
    VCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(bootstrapIcon(item.type.icon), tint, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip(item.type.label, tint)
                    if (item.isDraft) TagChip("Draft", StatusGrey)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OpenLinkButton(context: Context, url: String) {
    FilledTonalButton(onClick = { openUrl(context, url) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
        Text("  Open material")
    }
}

internal fun ContentType.tint(): Color = when (this) {
    ContentType.LiveDoc -> StatusBlue
    ContentType.CodeFile -> StatusPurple
    ContentType.SmartLink -> StatusGreen
    ContentType.LoomVideo -> StatusRed
    ContentType.CourseFile -> StatusAmber
    ContentType.Whiteboard -> VintowsAccent
}

/** "Subject" → "Subjects", "Chapter / Unit" → "Chapters / Units". */
internal fun pluralLabel(label: String): String =
    label.split("/").joinToString(" / ") { part ->
        val word = part.trim()
        when {
            word.isEmpty() -> word
            word.endsWith("s", ignoreCase = true) -> word
            word.endsWith("y", ignoreCase = true) && word.length > 1 && word[word.length - 2].lowercaseChar() !in "aeiou" -> word.dropLast(1) + "ies"
            else -> word + "s"
        }
    }

internal fun levelName(code: String): String = code.lowercase()

/** Opens an http(s) link in the browser. Other schemes from course data are ignored. */
internal fun openUrl(context: Context, url: String) {
    if (!isWebUrl(url)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser installed; nothing sensible to do.
    }
}
