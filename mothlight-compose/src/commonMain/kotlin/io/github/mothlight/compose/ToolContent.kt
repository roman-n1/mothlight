package io.github.mothlight.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.mothlight.*
import io.github.mothlight.compose.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NetworkContent(mothlight: Mothlight, modifier: Modifier) {
    val transactions by mothlight.network.transactions.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val rows = remember(transactions, query) { networkRows(transactions, query) }
    val selected = remember(transactions, selectedId) { selectedTransaction(transactions, selectedId) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(Res.string.search_http)) })
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(rows, key = { it.id }) { row ->
                ListItem(
                    headlineContent = { Text(row.title, maxLines = 2) },
                    supportingContent = { Text(row.subtitle + " · " + stringResource(Res.string.duration_ms, row.duration), fontFamily = FontFamily.Monospace) },
                    modifier = Modifier.clickable { selectedId = row.id },
                )
                HorizontalDivider()
            }
        }
        when (selected) {
            null -> Text(stringResource(Res.string.select_request), style = MaterialTheme.typography.bodySmall)
            else -> SelectionContainer(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.request), style = MaterialTheme.typography.titleMedium)
                    Text(requestDetails(selected, localizedBody(selected.requestBody)), fontFamily = FontFamily.Monospace)
                    Text(stringResource(Res.string.response), style = MaterialTheme.typography.titleMedium)
                    Text(responseDetails(selected, localizedBody(selected.responseBody)), fontFamily = FontFamily.Monospace)
                    Text(stringResource(Res.string.curl), style = MaterialTheme.typography.titleMedium)
                    Text(buildCurlCommand(selected), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
internal fun LogsContent(mothlight: Mothlight, modifier: Modifier) {
    val entries by mothlight.logs.items.collectAsState()
    val rows = remember(entries) { logRows(entries) }
    FeedContent(rows, modifier)
}

@Composable
internal fun EventsContent(mothlight: Mothlight, modifier: Modifier) {
    val entries by mothlight.events.items.collectAsState()
    val rows = remember(entries) { eventRows(entries) }
    FeedContent(rows, modifier)
}

@Composable
private fun FeedContent(rows: List<FeedRow>, modifier: Modifier) {
    SelectionContainer(modifier) {
        LazyColumn {
            item { Text(stringResource(Res.string.feed_hint), Modifier.padding(8.dp)) }
            items(rows, key = { it.id }) { row ->
                ListItem(headlineContent = { Text(row.message) }, supportingContent = { Text(row.label) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun localizedBody(body: String?): String = when (body) {
    null -> stringResource(Res.string.body_not_captured)
    "[body omitted: size limit]" -> stringResource(Res.string.body_too_large)
    "[body omitted: not JSON]" -> stringResource(Res.string.body_not_json)
    else -> body
}
