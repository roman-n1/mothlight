package io.github.mothlight.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mothlight.sample.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ArticleReader(demo: DemoSession, article: Article, saved: Boolean, busy: Boolean,
    back: () -> Unit, bookmark: () -> Unit) {
    val content by remember(demo, article.id) { demo.articleContent(article.id) }.collectAsState(null)
    var loading by remember { mutableStateOf(article.sourceUrl.isNotBlank()) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(article.id, attempt) {
        if (article.sourceUrl.isNotBlank()) {
            loading = true
            failed = false
            try { failed = !demo.loadArticle(article.id) } finally { loading = false }
        }
    }
    Column(Modifier.fillMaxSize().testTag("article-reader")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back, modifier = Modifier.testTag("article-back")) { Text(stringResource(Res.string.back_to_list)) }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = bookmark) {
                Text(stringResource(if (saved) Res.string.remove_saved else Res.string.save_article))
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("article-loading"))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 800.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(article.title, style = MaterialTheme.typography.headlineMedium)
                Text("${article.author} · DEV · ${article.minutes} ${stringResource(Res.string.read_minutes)}",
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                if (failed) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(if (content == null) Res.string.article_error else Res.string.article_cached))
                            TextButton(onClick = { attempt++ }, modifier = Modifier.testTag("article-retry")) { Text(stringResource(Res.string.retry)) }
                        }
                    }
                }
                val text = content?.text ?: if (article.sourceUrl.isBlank()) article.body else ""
                if (text.isNotEmpty()) SelectionContainer {
                    Text(text, Modifier.testTag("article-full-text"), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
