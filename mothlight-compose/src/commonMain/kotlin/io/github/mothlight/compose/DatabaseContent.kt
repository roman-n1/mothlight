package io.github.mothlight.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mothlight.*
import io.github.mothlight.compose.resources.*
import kotlinx.coroutines.flow.catch
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
    data class Ready(val page: DatabasePage) : PageState
}

@Composable
private fun DatabaseContent(source: DatabaseSource, modifier: Modifier) {
    val browser = remember(source) { DatabaseBrowser(source) }
    var selected by remember(source) { mutableStateOf<String?>(null) }
    var offset by remember(source, selected) { mutableStateOf(0) }
    val tables by produceState<TablesState>(TablesState.Loading, browser) {
        browser.tables().catch { value = TablesState.Failed }.collect { value = TablesState.Ready(it) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.database_hint), style = MaterialTheme.typography.bodySmall)
        when (val state = tables) {
            TablesState.Loading -> Text(stringResource(Res.string.loading))
            TablesState.Failed -> Text(stringResource(Res.string.database_error))
            is TablesState.Ready -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.names.forEach { name ->
                        FilterChip(selected = selected == name, onClick = { selected = name }, label = { Text(name) })
                    }
                }
                val table = selected
                when (table) {
                    null -> Text(stringResource(Res.string.select_table))
                    else -> DatabasePageContent(browser, table, offset, { offset = it }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DatabasePageContent(browser: DatabaseBrowser, table: String, offset: Int, onOffset: (Int) -> Unit, modifier: Modifier) {
    val state by produceState<PageState>(PageState.Loading, browser, table, offset) {
        value = PageState.Loading
        browser.page(table, offset = offset).catch { value = PageState.Failed }.collect { value = PageState.Ready(it) }
    }
    when (val current = state) {
        PageState.Loading -> Text(stringResource(Res.string.loading))
        PageState.Failed -> Text(stringResource(Res.string.database_error))
        is PageState.Ready -> {
            val page = current.page
            val nullText = stringResource(Res.string.null_value)
            val rows = remember(page, nullText) { databaseRows(page, nullText) }
            Column(modifier) {
                Text(stringResource(Res.string.database_page, page.totalRows.toString(), page.offset.toString()))
                Row {
                    TextButton(onClick = { onOffset(previousOffset(page)) }, enabled = hasPreviousPage(page)) { Text(stringResource(Res.string.previous)) }
                    TextButton(onClick = { onOffset(nextOffset(page)) }, enabled = hasNextPage(page)) { Text(stringResource(Res.string.next)) }
                }
                SelectionContainer(Modifier.weight(1f)) {
                    LazyColumn {
                        itemsIndexed(rows, key = { index, _ -> "${page.table}:${page.offset + index}" }) { _, row ->
                            Text(row, Modifier.padding(8.dp))
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
