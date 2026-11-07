package io.github.mothlight.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mothlight.*
import io.github.mothlight.compose.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NetworkContent(mothlight: Mothlight, modifier: Modifier) {
    val state = LocalPanelState.current.http(mothlight.network)
    val transactions by mothlight.network.transactions.collectAsState()
    val selected = selectedTransaction(transactions, state.selection)
    LaunchedEffect(transactions) {
        state.retainJson(transactions.map { it.id }.toSet())
        if (selected == RequestDetails.Prompt) state.selection = NetworkSelection.Unselected
    }
    BoxWithConstraints(modifier) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            MothlightDock(true, Modifier.fillMaxSize(), MothlightDockPlacement.End, state.split,
                resizeLabel = stringResource(Res.string.http_resize),
                panel = {
                    when (selected) {
                        RequestDetails.Prompt -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(Res.string.select_request), style = MaterialTheme.typography.bodyMedium)
                        }
                        is RequestDetails.Ready -> HttpDetails(selected.transaction, state, false, Modifier.fillMaxSize())
                    }
                }, content = { HttpList(mothlight, transactions, state, Modifier.fillMaxSize()) })
        } else when (selected) {
            RequestDetails.Prompt -> HttpList(mothlight, transactions, state, Modifier.fillMaxSize())
            is RequestDetails.Ready -> HttpDetails(selected.transaction, state, true, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun HttpList(mothlight: Mothlight, transactions: List<NetworkTransaction>, state: HttpPanelState, modifier: Modifier) {
    val rows = remember(transactions, state.query, state.filter) { filterHttp(transactions, state.query, state.filter) }
    val scope = rememberCoroutineScope()
    val enabled by mothlight.enabled.collectAsState()
    Column(modifier.testTag("http-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(state.query, {
            state.query = it
            scope.launch { state.list.scrollToItem(0) }
        }, Modifier.fillMaxWidth().testTag("http-search"), singleLine = true,
            label = { Text(stringResource(Res.string.http_search)) })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HttpFilter.entries.forEach { filter ->
                FilterChip(selected = state.filter == filter, onClick = {
                    state.filter = filter
                    scope.launch { state.list.scrollToItem(0) }
                }, modifier = Modifier.testTag("http-filter-${filter.name}"), label = { Text(httpFilterLabel(filter)) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.http_count, rows.size.toString(), transactions.size.toString()), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = mothlight.network::clear, enabled = transactions.isNotEmpty(), modifier = Modifier.testTag("http-clear")) { Text(stringResource(Res.string.http_clear)) }
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(when {
                    transactions.isNotEmpty() -> Res.string.http_no_matches
                    enabled -> Res.string.http_empty_recording
                    else -> Res.string.http_empty_disabled
                }), style = MaterialTheme.typography.bodyMedium)
            }
        } else LazyColumn(Modifier.fillMaxWidth().weight(1f), state = state.list) {
            items(rows, key = { it.id }) { tx ->
                val selected = state.selection == NetworkSelection.Request(tx.id)
                Surface(color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().testTag("http-row-${tx.id}")
                        .selectable(selected, role = Role.Button, onClick = { state.select(tx.id) })) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tx.method, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                            HttpStatus(tx.status, Modifier.weight(1f))
                            Text(httpDurationLabel(tx.status), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        Text(httpPath(tx.url), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(httpHost(tx.url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun HttpDetails(tx: NetworkTransaction, state: HttpPanelState, narrow: Boolean, modifier: Modifier) {
    Column(modifier.testTag("http-details").padding(horizontal = if (narrow) 0.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (narrow) TextButton(onClick = { state.selection = NetworkSelection.Unselected }, modifier = Modifier.testTag("http-back")) { Text(stringResource(Res.string.http_back)) }
            Text(tx.method, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
            HttpStatus(tx.status, Modifier.weight(1f))
            Text(httpDurationLabel(tx.status), style = MaterialTheme.typography.labelMedium)
        }
        SelectionContainer { Text(tx.url, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CopyRecordedText(stringResource(Res.string.http_copy_url), tx.url, "http-copy-url")
            CopyRecordedText(stringResource(Res.string.http_copy_curl), buildCurlCommand(tx), "http-copy-curl")
        }
        PrimaryTabRow(selectedTabIndex = state.detailTab.ordinal) {
            HttpDetailTab.entries.forEach { tab ->
                Tab(selected = state.detailTab == tab, onClick = { state.detailTab = tab; state.detailScroll = ScrollState(0) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("http-tab-${tab.name}")) {
                    Text(httpTabLabel(tab), Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (state.detailTab) {
                HttpDetailTab.Overview -> Column(Modifier.fillMaxSize().verticalScroll(state.detailScroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HttpSummaryField(stringResource(Res.string.http_url), tx.url)
                    HttpSummaryField(stringResource(Res.string.http_method), tx.method)
                    HttpSummaryField(stringResource(Res.string.http_status), statusText(tx.status))
                    HttpSummaryField(stringResource(Res.string.http_duration), httpDurationLabel(tx.status))
                    Text(stringResource(Res.string.http_capture_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HttpDetailTab.Request -> HttpPayload(tx.requestHeaders, tx.requestBody, false, state.json(tx.id, HttpDetailTab.Request))
                HttpDetailTab.Response -> HttpPayload(tx.responseHeaders, tx.responseBody, tx.status == Status.InProgress, state.json(tx.id, HttpDetailTab.Response))
            }
        }
    }
}

@Composable
private fun HttpSummaryField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace) }
    }
}

@Composable
private fun HttpHeaders(headers: Map<String, String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.http_headers), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (headers.isNotEmpty()) CopyRecordedText(stringResource(Res.string.http_copy), headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }, "http-copy-headers")
    }
    if (headers.isEmpty()) Text(stringResource(Res.string.http_no_headers), style = MaterialTheme.typography.bodySmall)
    headers.forEach { (name, value) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()
        }
    }
}

@Composable
private fun HttpPayload(headers: Map<String, String>, body: CapturedBody, waiting: Boolean, state: JsonBodyState) {
    val json = remember(body) { if (body is CapturedBody.Text) parseHttpJson(body.value) else HttpJson.Plain }
    val tree = remember(json, state.expandedPaths) {
        when (json) {
            HttpJson.Plain -> JsonTreeRows(emptyList(), false)
            is HttpJson.Structured -> jsonTreeRows(json.root, state.expandedPaths)
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("http-payload"), state = state.list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (waiting) item { Text(stringResource(Res.string.http_waiting)) }
        item { HttpHeaders(headers) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.http_body), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (body is CapturedBody.Text) CopyRecordedText(stringResource(Res.string.http_copy), body.value, "http-copy-body")
            }
        }
        if (json is HttpJson.Structured) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(state.mode == JsonBodyMode.Tree, { state.mode = JsonBodyMode.Tree }, modifier = Modifier.testTag("json-mode-tree"), label = { Text(stringResource(Res.string.json_tree)) })
                FilterChip(state.mode == JsonBodyMode.Text, { state.mode = JsonBodyMode.Text }, modifier = Modifier.testTag("json-mode-text"), label = { Text(stringResource(Res.string.json_text)) })
                if (state.mode == JsonBodyMode.Tree) TextButton(onClick = { state.expandedPaths = emptySet() }) { Text(stringResource(Res.string.json_collapse_all)) }
            }
        }
        if (json is HttpJson.Structured && state.mode == JsonBodyMode.Tree) {
            items(tree.rows, key = { "json:${it.path}" }) { row -> JsonNode(row) { state.toggle(row.path) } }
            if (tree.truncated) item { Text(stringResource(Res.string.json_tree_limit)) }
        } else item {
            if (body is CapturedBody.Text) {
                val formatted = remember(body.value) { formattedHttpBody(body.value) }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
                    SelectionContainer {
                        Text(formatted, Modifier.fillMaxWidth().padding(12.dp).horizontalScroll(rememberScrollState()).testTag("http-body"),
                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            } else Text(localizedBody(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun JsonNode(row: JsonTreeRow, toggle: () -> Unit) {
    val description = stringResource(if (row.expanded) Res.string.json_expanded else Res.string.json_collapsed)
    val rootLabel = stringResource(Res.string.json_root)
    val tone = when (row.kind) {
        JsonNodeKind.String -> MaterialTheme.colorScheme.primary
        JsonNodeKind.Number, JsonNodeKind.Boolean -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().testTag("json-node:${row.path}")
        .then(if (row.expandable) Modifier.clickable(role = Role.Button, onClick = toggle).semantics { stateDescription = description } else Modifier)
        .padding(start = (row.depth.coerceAtMost(12) * 12).dp, end = 8.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (row.expandable) { if (row.expanded) "▾" else "▸" } else "", Modifier.width(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (row.depth == 0) rootLabel else row.name + ":", Modifier.widthIn(max = 180.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(row.preview, Modifier.weight(1f), color = tone, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HttpStatus(status: Status, modifier: Modifier = Modifier) {
    val color = when (status) {
        is Status.Failed -> MaterialTheme.colorScheme.error
        is Status.Completed -> if (status.code >= 400) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Status.InProgress -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(statusText(status), modifier, color = color, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun httpDurationLabel(status: Status): String = if (status == Status.InProgress) "—"
    else stringResource(Res.string.duration_ms, durationText(status))

@Composable
private fun httpFilterLabel(filter: HttpFilter): String = stringResource(when (filter) {
    HttpFilter.All -> Res.string.http_all
    HttpFilter.HttpErrors -> Res.string.http_errors
    HttpFilter.TransportErrors -> Res.string.http_transport_errors
    HttpFilter.InProgress -> Res.string.http_in_progress
    HttpFilter.Slow -> Res.string.http_slow
})

@Composable
private fun httpTabLabel(tab: HttpDetailTab): String = stringResource(when (tab) {
    HttpDetailTab.Overview -> Res.string.http_overview
    HttpDetailTab.Request -> Res.string.request
    HttpDetailTab.Response -> Res.string.response
})
