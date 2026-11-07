package io.github.mothlight.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mothlight.*
import io.github.mothlight.compose.resources.*
import org.jetbrains.compose.resources.stringResource

private sealed interface SqlSelection {
    data object Overview : SqlSelection
    data class Query(val sql: String) : SqlSelection
}

@Composable
internal fun SqlContent(mothlight: Mothlight, modifier: Modifier) {
    val snapshot by mothlight.sql.snapshot.collectAsState()
    var query by remember { mutableStateOf("") }
    var showEvents by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SqlSort.Total) }
    var selection by remember { mutableStateOf<SqlSelection>(SqlSelection.Overview) }
    val summaryScroll = rememberLazyListState()
    val eventsScroll = rememberLazyListState()
    val selected = selection
    val showDetail = selected is SqlSelection.Query &&
        (snapshot.queries.any { it.sql == selected.sql } || snapshot.events.any { it.sql == selected.sql })
    LaunchedEffect(selected, showDetail) {
        if (!showDetail) selection = SqlSelection.Overview
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.sql_hint), style = MaterialTheme.typography.bodySmall)
        if (selected is SqlSelection.Query && showDetail) {
            SqlDetails(snapshot, selected.sql) { selection = SqlSelection.Overview }
        } else {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("sql-search"),
                label = { Text(stringResource(Res.string.search_sql)) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showEvents, onClick = { showEvents = false },
                    label = { Text(stringResource(Res.string.sql_statistics)) })
                FilterChip(selected = showEvents, onClick = { showEvents = true },
                    modifier = Modifier.testTag("sql-executions"),
                    label = { Text(stringResource(Res.string.sql_executions)) })
            }
            if (showEvents) {
                val events = remember(snapshot, query) { sqlEvents(snapshot, query) }
                if (events.isEmpty()) Text(stringResource(Res.string.sql_empty))
                LazyColumn(Modifier.weight(1f), state = eventsScroll) {
                    items(events, key = { it.id }) { event ->
                        SqlEventRow(event, showSql = true, modifier = Modifier.clickable {
                            selection = SqlSelection.Query(event.sql)
                        })
                        HorizontalDivider()
                    }
                }
            } else {
                SqlSortPicker(sort) { sort = it }
                val stats = remember(snapshot, query, sort) { sqlStats(snapshot, query, sort) }
                if (stats.isEmpty()) Text(stringResource(Res.string.sql_empty))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val wide = maxWidth >= 720.dp
                    Column {
                        if (wide) {
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                                Text(stringResource(Res.string.sql_query), Modifier.weight(2.4f),
                                    style = MaterialTheme.typography.labelLarge)
                                for (metric in SqlSort.entries) {
                                    Text(stringResource(metric.label()), Modifier.weight(1f), textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        LazyColumn(state = summaryScroll) {
                            items(stats, key = { it.sql }) { stat ->
                                SqlSummaryRow(stat, wide) { selection = SqlSelection.Query(stat.sql) }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlSortPicker(sort: SqlSort, onSort: (SqlSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("sql-sort")) {
            Text(stringResource(Res.string.sql_sort, stringResource(sort.label())))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (metric in SqlSort.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(metric.label())) },
                    onClick = { onSort(metric); open = false },
                    modifier = Modifier.testTag("sql-sort-${metric.name}"),
                )
            }
        }
    }
}

private fun SqlSort.label() = when (this) {
    SqlSort.Count -> Res.string.sql_executions
    SqlSort.Total -> Res.string.sql_total
    SqlSort.Last -> Res.string.sql_last
    SqlSort.Errors -> Res.string.sql_errors
}

@Composable
private fun SqlSummaryRow(stat: SqlQueryStat, wide: Boolean, onOpen: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().testTag("sql-query:${stat.sql}").clickable(onClick = onOpen).padding(10.dp)
    if (wide) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            SqlPreview(stat.sql, Modifier.weight(2.4f))
            for (metric in SqlSort.entries) SqlMetric(stat, metric, Modifier.weight(1f), labeled = false)
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SqlPreview(stat.sql)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (metric in SqlSort.entries) SqlMetric(stat, metric, Modifier.weight(1f), labeled = true)
            }
        }
    }
}

@Composable
private fun SqlMetric(stat: SqlQueryStat, metric: SqlSort, modifier: Modifier, labeled: Boolean) {
    Column(modifier, horizontalAlignment = if (labeled) Alignment.Start else Alignment.End) {
        if (labeled) Text(stringResource(metric.label()), style = MaterialTheme.typography.labelSmall)
        Text(metric.value(stat).toString(), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = if (metric == SqlSort.Errors && stat.failures > 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SqlPreview(sql: String, modifier: Modifier = Modifier) {
    Text(sql, modifier, maxLines = 2, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
}

@Composable
private fun ColumnScope.SqlDetails(snapshot: SqlTraceSnapshot, sql: String, onBack: () -> Unit) {
    val events = remember(snapshot, sql) { snapshot.events.filter { it.sql == sql }.asReversed() }
    val stats = remember(snapshot, sql) { snapshot.queries.filter { it.sql == sql } }
    val formatted = remember(sql) { formatRecordedSql(sql) }
    var showRaw by remember(sql) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("sql-back")) {
            Text(stringResource(Res.string.previous))
        }
        CopyRecordedText(stringResource(Res.string.sql_copy), sql, "sql-copy")
        if (formatted != sql) {
            TextButton(onClick = { showRaw = !showRaw }, modifier = Modifier.testTag("sql-format")) {
                Text(stringResource(if (showRaw) Res.string.sql_formatted else Res.string.sql_original))
            }
        }
    }
    LazyColumn(Modifier.weight(1f).testTag("sql-details"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()) {
                SelectionContainer(Modifier.padding(12.dp)) {
                    Text(if (showRaw) sql else formatted,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
        items(stats) { stat ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (metric in SqlSort.entries) SqlMetric(stat, metric, Modifier.weight(1f), labeled = true)
            }
        }
        item {
            Text(stringResource(Res.string.sql_retained, events.size.toString()),
                style = MaterialTheme.typography.titleSmall)
            if (events.isEmpty()) Text(stringResource(Res.string.sql_no_retained), style = MaterialTheme.typography.bodySmall)
        }
        items(events, key = { it.id }) { event ->
            SqlEventRow(event, showSql = false)
            HorizontalDivider()
        }
    }
}

@Composable
private fun SqlEventRow(event: SqlExecution, showSql: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showSql) SqlPreview(event.sql)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#${event.id}", style = MaterialTheme.typography.labelMedium)
            Text(stringResource(Res.string.duration_ms, event.durationMillis.toString()),
                style = MaterialTheme.typography.labelMedium)
            when (val outcome = event.outcome) {
                SqlOutcome.Succeeded -> Text(stringResource(Res.string.sql_succeeded),
                    style = MaterialTheme.typography.labelMedium)
                is SqlOutcome.Failed -> Text(outcome.category.ifEmpty { stringResource(Res.string.sql_errors) },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        if (!showSql) Text(stringResource(Res.string.sql_start_time, event.startedAtMillis.toString()),
            style = MaterialTheme.typography.bodySmall)
    }
}
