package io.github.mothlight.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import io.github.mothlight.*
import io.github.mothlight.compose.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

/** Remember the returned tool collection in the host. Removing the tool cancels its subscriptions. */
fun databaseTool(source: DatabaseSource, id: String = "database"): MothlightTool = MothlightTool(
    id, { stringResource(Res.string.database) }, { DatabaseContent(source, it) },
)

private sealed interface TablesState {
    data object Loading : TablesState
    data object Failed : TablesState
    data class Ready(val names: List<String>) : TablesState
}

private sealed interface PageState {
    data object Loading : PageState
    data object Failed : PageState
    data class Ready(val page: DatabasePage, val delta: DatabaseDelta) : PageState
}

@Composable
private fun DatabaseContent(source: DatabaseSource, modifier: Modifier) {
    val browser = remember(source) { DatabaseBrowser(source) }
    var selected by remember(source) { mutableStateOf<TableSelection>(TableSelection.Prompt) }
    val tables by produceState<TablesState>(TablesState.Loading, browser) {
        browser.tables().distinctUntilChanged().catch { value = TablesState.Failed }
            .collect { value = TablesState.Ready(it) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val state = tables) {
            TablesState.Loading -> Text(stringResource(Res.string.loading))
            TablesState.Failed -> Text(stringResource(Res.string.database_error))
            is TablesState.Ready -> {
                val table = when (val selection = selected) {
                    TableSelection.Prompt -> ""
                    is TableSelection.Chosen -> selection.name
                }
                TablePicker(state.names, table) { selected = TableSelection.Chosen(it) }
                if (table in state.names) {
                    key(source, table) {
                        var showSchema by remember { mutableStateOf(false) }
                        var offset by remember { mutableStateOf(0) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !showSchema, onClick = { showSchema = false },
                                modifier = Modifier.testTag("database-data"),
                                label = { Text(stringResource(Res.string.database_data)) })
                            FilterChip(selected = showSchema, onClick = { showSchema = true },
                                modifier = Modifier.testTag("database-schema"),
                                label = { Text(stringResource(Res.string.database_schema)) })
                        }
                        if (showSchema) DatabaseSchemaContent(browser, table, Modifier.weight(1f))
                        else key(offset) {
                            DatabasePageContent(source, browser, table, offset, { offset = it }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TablePicker(names: List<String>, table: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (names.isEmpty()) {
        Text(stringResource(Res.string.database_no_tables))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { open = !open }, modifier = Modifier.weight(1f).testTag("database-picker")) {
            Text(table.ifEmpty { stringResource(Res.string.select_table) }, Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (open) "▴" else "▾")
        }
        Text(stringResource(Res.string.database_table_count, names.size.toString()),
            style = MaterialTheme.typography.labelMedium)
    }
    if (open) {
        var query by remember { mutableStateOf("") }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().testTag("database-table-search"),
                    label = { Text(stringResource(Res.string.database_find_table)) }, singleLine = true)
                val filtered = remember(names, query) { names.filter { it.contains(query.trim(), ignoreCase = true) } }
                if (filtered.isEmpty()) Text(stringResource(Res.string.database_no_matches))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                    items(filtered, key = { it }) { name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); open = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun DatabasePageContent(
    source: DatabaseSource, browser: DatabaseBrowser, table: String, offset: Int,
    onOffset: (Int) -> Unit, modifier: Modifier,
) {
    val state by produceState<PageState>(PageState.Loading, browser, table, offset) {
        var schema: DatabaseSchema = DatabaseSchema.Unsupported
        browser.page(table, offset = offset)
            .catch { value = PageState.Failed }
            .collect { page ->
                val previous = value
                if (previous !is PageState.Ready) {
                    schema = try {
                        source.schema(table)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        DatabaseSchema.Unsupported
                    }
                }
                val compared = databaseDelta(if (previous is PageState.Ready) previous.page else page, page, schema)
                val delta = if (previous is PageState.Ready && compared.keyColumns.isNotEmpty() &&
                    compared.changes.isEmpty() && previous.delta.keyColumns == compared.keyColumns
                ) previous.delta else compared
                value = PageState.Ready(page, delta)
            }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val current = state) {
            PageState.Loading -> Text(stringResource(Res.string.loading))
            PageState.Failed -> Text(stringResource(Res.string.database_error))
            is PageState.Ready -> DatabasePageView(current, onOffset)
        }
    }
}

@Composable
private fun ColumnScope.DatabasePageView(current: PageState.Ready, onOffset: (Int) -> Unit) {
    val page = current.page
    val delta = current.delta
    var showChanges by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val start = if (page.rows.isEmpty()) "—" else (page.offset + 1L).toString()
        val end = if (page.rows.isEmpty()) "—" else (page.offset.toLong() + page.rows.size).toString()
        Text(stringResource(Res.string.database_range, start, end, page.totalRows.toString()),
            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { onOffset(previousOffset(page)) }, enabled = hasPreviousPage(page)) {
            Text(stringResource(Res.string.previous))
        }
        TextButton(onClick = { onOffset(nextOffset(page)) }, enabled = hasNextPage(page)) {
            Text(stringResource(Res.string.next))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (delta.keyColumns.isEmpty()) Res.string.database_untracked else Res.string.database_live),
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(selected = showChanges, onClick = { showChanges = !showChanges },
            modifier = Modifier.testTag("database-changes"),
            label = { Text(stringResource(Res.string.database_changes, delta.changes.size.toString())) })
    }
    if (showChanges) DatabaseChangesView(page, delta, Modifier.weight(1f))
    else DatabaseGrid(page, delta, Modifier.weight(1f))
}

@Composable
private fun DatabaseGrid(page: DatabasePage, delta: DatabaseDelta, modifier: Modifier) {
    val nullText = stringResource(Res.string.null_value)
    var inspected by remember { mutableStateOf(emptyList<DatabaseCell>()) }
    val highlightedColumns = remember(delta) {
        delta.changes.associate { change ->
            change.key to change.after.indices.filter { index ->
                change.kind == DatabaseChangeKind.Appeared || change.before[index] != change.after[index]
            }.toSet()
        }
    }
    val keyIndices = remember(page.columns, delta.keyColumns) { delta.keyColumns.map(page.columns::indexOf) }
    val columns = page.columns.take(32)
    if (page.rows.isEmpty()) {
        Text(stringResource(Res.string.database_no_rows))
    } else {
        if (columns.size < page.columns.size) {
            Text(stringResource(Res.string.database_grid_limit, columns.size.toString()),
                style = MaterialTheme.typography.bodySmall)
        }
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val columnWidth = ((maxWidth - 48.dp) / columns.size.coerceAtLeast(1)).coerceIn(124.dp, 240.dp)
            val gridWidth = 48.dp + columnWidth * columns.size
            val needsScroll = gridWidth > maxWidth
            Column {
                if (needsScroll) {
                    Text(stringResource(Res.string.database_scroll_columns),
                        Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.horizontalScroll(rememberScrollState()).width(gridWidth)) {
                    Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                        GridCell("#", 48.dp, header = true)
                        columns.forEach { GridCell(it, columnWidth, header = true) }
                    }
                    LazyColumn(Modifier.testTag("database-grid")) {
                        itemsIndexed(page.rows) { index, row ->
                            val key = keyIndices.map(row::get)
                            val highlighted = highlightedColumns[key].orEmpty()
                            Row(Modifier.fillMaxWidth().clickable { inspected = row }) {
                                GridCell((page.offset.toLong() + index + 1L).toString(), 48.dp)
                                columns.forEachIndexed { column, _ ->
                                    val cell = row.getOrElse(column) { DatabaseCell.SqlNull }
                                    GridCell(cellText(cell, nullText), columnWidth, highlighted = column in highlighted)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    if (inspected.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { inspected = emptyList() },
            title = { Text(stringResource(Res.string.database_row_detail)) },
            text = {
                SelectionContainer {
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(page.columns) { index, name ->
                            Column {
                                Text(name, style = MaterialTheme.typography.labelMedium)
                                Text(cellText(inspected.getOrElse(index) { DatabaseCell.SqlNull }, nullText))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { inspected = emptyList() }) { Text(stringResource(Res.string.database_close)) } },
        )
    }
}

@Composable
private fun GridCell(value: String, width: Dp, header: Boolean = false, highlighted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.width(width).height(40.dp)
        .background(if (highlighted) colors.secondaryContainer else Color.Transparent)
        .padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (highlighted) colors.onSecondaryContainer else colors.onSurface,
            style = if (header) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
    }
}

@Composable
private fun DatabaseChangesView(page: DatabasePage, delta: DatabaseDelta, modifier: Modifier) {
    val nullText = stringResource(Res.string.null_value)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.database_change_scope), style = MaterialTheme.typography.bodySmall)
        if (delta.changes.isEmpty()) Text(stringResource(Res.string.database_no_changes))
        SelectionContainer {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(delta.changes) { change ->
                    val label = stringResource(when (change.kind) {
                        DatabaseChangeKind.Appeared -> Res.string.database_appeared
                        DatabaseChangeKind.Updated -> Res.string.database_updated
                        DatabaseChangeKind.Left -> Res.string.database_left
                    })
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(delta.keyColumns.zip(change.key).joinToString(" · ") { (name, value) ->
                                "$name = ${cellText(value, nullText)}"
                            }, style = MaterialTheme.typography.titleSmall)
                            page.columns.forEachIndexed { index, name ->
                                val value = when (change.kind) {
                                    DatabaseChangeKind.Appeared -> cellText(change.after[index], nullText)
                                    DatabaseChangeKind.Left -> cellText(change.before[index], nullText)
                                    DatabaseChangeKind.Updated -> {
                                        if (change.before[index] == change.after[index]) return@forEachIndexed
                                        "${cellText(change.before[index], nullText)} → ${cellText(change.after[index], nullText)}"
                                    }
                                }
                                if (name !in delta.keyColumns) Text("$name · $value", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
