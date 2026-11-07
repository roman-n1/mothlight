package io.github.mothlight.sample

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.mothlight.compose.*
import io.github.mothlight.sample.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Лист · Mothlight demo",
        state = rememberWindowState(width = 1240.dp, height = 900.dp)) {
        var dark by remember { mutableStateOf(false) }
        DemoTheme(dark) { ReaderApp(dark, { dark = !dark }) }
    }
}

@Composable
internal fun ReaderApp(dark: Boolean, toggleTheme: () -> Unit, demo: DemoSession = remember { DemoSession() }) {
    val scope = rememberCoroutineScope()
    val panelState = remember { MothlightPanelState() }
    val dockState = remember { MothlightDockState() }
    var panelVisible by remember { mutableStateOf(false) }
    var placement by remember { mutableStateOf(MothlightDockPlacement.Bottom) }
    val busy by demo.busy.collectAsState()
    val error by demo.error.collectAsState()
    val ready by demo.ready.collectAsState()
    val articles by demo.articles.collectAsState()
    val publications by demo.publications.collectAsState()
    val bookmarks by demo.bookmarks.collectAsState()
    var destination by remember { mutableStateOf(ReaderDestination.Feed) }
    var selectedArticle by remember { mutableStateOf<Article?>(null) }
    DisposableEffect(demo) { onDispose { demo.close() } }
    LaunchedEffect(demo) { demo.firstFrame(); demo.initialize() }
    val tools = remember(demo) {
        mothlightTools(demo.mothlight) {
            tool(databaseTool(demo.databaseSource))
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.reader_name), style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.reader_tagline), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = toggleTheme) { Text(stringResource(if (dark) Res.string.light_theme else Res.string.dark_theme)) }
                OutlinedButton(modifier = Modifier.testTag("debug-toggle"), onClick = { panelVisible = !panelVisible }) {
                    Text(stringResource(if (panelVisible) Res.string.hide_debug else Res.string.open_debug))
                }
            }
            HorizontalDivider()
            MothlightDock(panelVisible, Modifier.weight(1f).fillMaxWidth(), placement, dockState,
                resizeLabel = stringResource(Res.string.resize_panel),
                panel = {
                    Surface(Modifier.fillMaxSize()) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.developer_tools), Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge)
                                TextButton(modifier = Modifier.testTag("dock-placement"), onClick = {
                                    placement = if (placement == MothlightDockPlacement.Bottom) MothlightDockPlacement.End else MothlightDockPlacement.Bottom
                                }) { Text(stringResource(if (placement == MothlightDockPlacement.Bottom) Res.string.dock_right else Res.string.dock_bottom)) }
                                TextButton(modifier = Modifier.testTag("dock-close"), onClick = { panelVisible = false }) { Text(stringResource(Res.string.close_panel)) }
                            }
                            MothlightPanel(demo.mothlight, Modifier.weight(1f).fillMaxWidth(), tools, panelState)
                        }
                    }
                },
            ) {
                val article = selectedArticle
                if (article == null) {
                    ReaderContent(destination, { destination = it }, articles, publications, bookmarks,
                        busy, error, ready,
                        refresh = { scope.launch { if (ready) demo.refresh() else demo.initialize() } },
                        subscribe = { scope.launch { demo.toggleSubscription(it) } },
                        bookmark = { scope.launch { demo.toggleBookmark(it) } },
                        open = { selectedArticle = it })
                } else {
                    key(article.id) {
                        ArticleReader(demo, article, bookmarks.any { it.articleId == article.id }, busy,
                            back = { selectedArticle = null },
                            bookmark = { scope.launch { demo.toggleBookmark(article.id) } })
                    }
                }
            }
            MemoryUsageOverlay(demo.mothlight, Modifier.padding(horizontal = 12.dp))
        }
    }

}
