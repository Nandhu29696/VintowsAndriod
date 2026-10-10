package com.vintows.app.feature.courses.ui

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.DetailTopBar
import com.vintows.app.core.designsystem.component.ErrorState
import com.vintows.app.core.designsystem.component.IconBadge
import com.vintows.app.core.designsystem.component.LoadingState
import com.vintows.app.core.designsystem.component.TagChip
import com.vintows.app.core.designsystem.icon.bootstrapIcon
import com.vintows.app.core.designsystem.theme.StatusGrey
import com.vintows.app.core.ui.Section
import com.vintows.app.feature.courses.domain.ContentItem
import com.vintows.app.feature.courses.domain.ContentType
import kotlinx.coroutines.launch

@Composable
fun ContentRoute(
    onBack: () -> Unit,
    viewModel: ContentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContentScreen(state, onBack, viewModel::retry)
}

@Composable
fun ContentScreen(state: ContentUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION") // LocalClipboard needs a suspend API; plain text copy is all we need here.
    val clipboard = LocalClipboardManager.current
    val item = (state.item as? Section.Loaded)?.data

    Scaffold(
        topBar = {
            DetailTopBar(
                title = state.title,
                subtitle = state.type?.label,
                onBack = onBack,
                actions = {
                    if (item?.type == ContentType.CodeFile && !item.code.isNullOrEmpty()) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(item.code))
                            scope.launch { snackbar.showSnackbar("Code copied") }
                        }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy code") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val section = state.item) {
                Section.Loading -> LoadingState()
                is Section.Failed -> ErrorState(section.message, onRetry = onRetry)
                is Section.Loaded -> ContentBody(section.data)
            }
        }
    }
}

@Composable
private fun ContentBody(item: ContentItem) {
    when (item.type) {
        ContentType.LiveDoc -> HtmlView(item.html.orEmpty())
        ContentType.CodeFile -> CodeView(item.code.orEmpty(), item.language)
        ContentType.SmartLink, ContentType.LoomVideo, ContentType.CourseFile -> LinkView(item)
        ContentType.Whiteboard -> InfoView(
            item,
            title = "Open whiteboards on the web",
            message = "Whiteboards are interactive drawings and are best viewed on a larger screen at vintows.com.",
        )
    }
}

/**
 * Live docs are HTML written in the web editor. Shown in a WebView with JavaScript, file and content
 * access off; links open in the browser instead of inside the app.
 */
@Composable
private fun HtmlView(rawHtml: String) {
    val html = remember(rawHtml) { stripEditorPlaceholder(rawHtml) }
    if (html.isBlank()) {
        EmptyContent("This document is empty.")
        return
    }
    val text = MaterialTheme.colorScheme.onSurface.toArgb()
    val link = MaterialTheme.colorScheme.secondary.toArgb()
    val page = remember(html, text, link) { wrapHtml(html, cssColor(text), cssColor(link)) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        openUrl(view.context, request.url.toString())
                        return true
                    }
                }
            }
        },
        update = { it.loadDataWithBaseURL(null, page, "text/html", "utf-8", null) },
    )
}

private fun cssColor(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

internal fun wrapHtml(body: String, textColor: String, linkColor: String): String = """
    <!doctype html><html><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
      body { font-family: sans-serif; font-size: 16px; line-height: 1.6; color: $textColor; margin: 20px; word-wrap: break-word; }
      a { color: $linkColor; }
      img, video, iframe, table { max-width: 100%; }
      pre, code { background: rgba(127,127,127,.12); border-radius: 6px; padding: 2px 4px; overflow-x: auto; }
      pre { padding: 12px; }
    </style></head><body>$body</body></html>
""".trimIndent()

@Composable
private fun CodeView(code: String, language: String?) {
    if (code.isBlank()) {
        EmptyContent("This code file is empty.")
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        language?.let { TagChip(it, ContentType.CodeFile.tint(), Modifier.padding(bottom = 10.dp)) }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .background(Color(0xFF0F1B2D), MaterialTheme.shapes.medium)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SelectionContainer {
                Text(
                    code,
                    color = Color(0xFFE6EDF6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun LinkView(item: ContentItem) {
    val context = LocalContext.current
    val url = item.url
    val (title, message, action) = when (item.type) {
        ContentType.LoomVideo -> Triple("Watch the video", "The lesson video opens in your browser or the Loom app.", "Play video")
        ContentType.CourseFile -> Triple(
            "Course file",
            listOfNotNull(item.mimeType, item.fileSize?.let(::fileSizeLabel)).joinToString(" · ").ifEmpty { "Opens in a viewer app." },
            if (item.allowDownload) "Open file" else "View file",
        )
        else -> Triple("External resource", url ?: "", "Open link")
    }
    when {
        url.isNullOrBlank() -> EmptyContent("No link has been added to this material yet.")
        !isWebUrl(url) -> EmptyContent("This link can't be opened on a phone.")
        else -> InfoView(item, title, message) {
            Button(onClick = { openUrl(context, url) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text("  $action")
            }
        }
    }
}

@Composable
private fun InfoView(item: ContentItem, title: String, message: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(bootstrapIcon(item.type.icon), item.type.tint(), size = 72.dp, iconSize = 36.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (message.isNotBlank()) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
private fun EmptyContent(message: String) {
    Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = StatusGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The web editor saves its empty-state hint ("Press <kbd>space</kbd> to Ask Vintows…") into new docs.
 * Paragraphs holding that hint are removed; what's left is blank for an untouched doc.
 */
internal fun stripEditorPlaceholder(html: String): String =
    html.replace(Regex("<p>(?:(?!</p>).)*he-kbd-hint(?:(?!</p>).)*</p>", RegexOption.DOT_MATCHES_ALL), "")
        .let { if (it.replace(Regex("<[^>]+>|&nbsp;|\\s"), "").isEmpty()) "" else it }

internal fun fileSizeLabel(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

internal fun isWebUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
