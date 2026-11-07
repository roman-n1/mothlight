package io.github.mothlight.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mothlight.sample.resources.*
import org.jetbrains.compose.resources.stringResource

enum class ReaderDestination { Feed, Saved, Subscriptions }

internal fun visibleArticles(destination: ReaderDestination, articles: List<Article>, publications: List<Publication>, bookmarks: List<Bookmark>): List<Article> =
    when (destination) {
        ReaderDestination.Feed -> articles.filter { article -> article.sourceUrl.isNotBlank() && publications.any { it.id == article.publicationId && it.subscribed } }
        ReaderDestination.Saved -> articles.filter { article -> bookmarks.any { it.articleId == article.id } }
        ReaderDestination.Subscriptions -> emptyList()
    }

@Composable
internal fun ReaderContent(
    destination: ReaderDestination, navigate: (ReaderDestination) -> Unit,
    articles: List<Article>, publications: List<Publication>, bookmarks: List<Bookmark>,
    busy: Boolean, error: Boolean, ready: Boolean, refresh: () -> Unit,
    subscribe: (String) -> Unit, bookmark: (Long) -> Unit, open: (Article) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 680.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(210.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.your_library), Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.labelLarge)
                        ReaderDestination.entries.forEach { route ->
                            FilterChip(destination == route, { navigate(route) }, { Text(destinationLabel(route)) }, Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(28.dp))
                        Text(stringResource(Res.string.reader_note), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ReaderBody(destination, articles, publications, bookmarks, busy, error, ready, refresh, subscribe, bookmark, open, Modifier.weight(1f))
            }
        } else {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderDestination.entries.forEach { route ->
                        FilterChip(destination == route, { navigate(route) }, { Text(destinationLabel(route), maxLines = 1) })
                    }
                }
                ReaderBody(destination, articles, publications, bookmarks, busy, error, ready, refresh, subscribe, bookmark, open, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReaderBody(
    destination: ReaderDestination, articles: List<Article>, publications: List<Publication>, bookmarks: List<Bookmark>,
    busy: Boolean, error: Boolean, ready: Boolean, refresh: () -> Unit,
    subscribe: (String) -> Unit, bookmark: (Long) -> Unit, open: (Article) -> Unit, modifier: Modifier,
) {
    val visible = visibleArticles(destination, articles, publications, bookmarks)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(destinationLabel(destination), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(when (destination) {
                        ReaderDestination.Feed -> Res.string.feed_hint
                        ReaderDestination.Saved -> Res.string.saved_hint
                        ReaderDestination.Subscriptions -> Res.string.subscriptions_hint
                    }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled = !busy, onClick = refresh) { Text(stringResource(Res.string.refresh_feed)) }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error) item {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(Res.string.operation_error))
                    TextButton(enabled = !busy, onClick = refresh) { Text(stringResource(Res.string.retry)) }
                }
            }
        }
        if (destination == ReaderDestination.Subscriptions) {
            items(publications, key = { it.id }) { publication ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(publication.title, style = MaterialTheme.typography.titleLarge)
                        Text(publication.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (publication.subscribed) {
                            OutlinedButton(enabled = !busy && ready, onClick = { subscribe(publication.id) }) { Text(stringResource(Res.string.unsubscribe)) }
                        } else {
                            Button(enabled = !busy && ready, onClick = { subscribe(publication.id) }) { Text(stringResource(Res.string.subscribe)) }
                        }
                    }
                }
            }
        } else {
            if (visible.isEmpty() && !busy) item {
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(if (destination == ReaderDestination.Saved) Res.string.empty_saved else Res.string.empty_feed),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (destination == ReaderDestination.Saved) Res.string.empty_saved_hint else Res.string.empty_feed_hint))
                    }
                }
            }
            items(visible, key = { it.id }) { article ->
                val saved = bookmarks.any { it.articleId == article.id }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${publications.firstOrNull { it.id == article.publicationId }?.title.orEmpty()}  ·  ${article.minutes} ${stringResource(Res.string.read_minutes)}",
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text(article.title, style = MaterialTheme.typography.titleLarge)
                        Text(article.summary, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(modifier = Modifier.testTag("read-article"), onClick = { open(article) }) { Text(stringResource(Res.string.read_article)) }
                            TextButton(enabled = !busy && ready, onClick = { bookmark(article.id) }) {
                                Text(stringResource(if (saved) Res.string.remove_saved else Res.string.save_article))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun destinationLabel(destination: ReaderDestination): String = stringResource(when (destination) {
    ReaderDestination.Feed -> Res.string.feed
    ReaderDestination.Saved -> Res.string.saved
    ReaderDestination.Subscriptions -> Res.string.subscriptions
})
