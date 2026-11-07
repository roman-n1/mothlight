package io.github.mothlight.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mothlight.*
import io.github.mothlight.compose.resources.*
import kotlinx.coroutines.flow.catch
import org.jetbrains.compose.resources.stringResource

private sealed interface SchemaState {
    data object Loading : SchemaState
    data object Failed : SchemaState
    data class Ready(val schema: DatabaseSchema) : SchemaState
}

@Composable
internal fun DatabaseSchemaContent(browser: DatabaseBrowser, table: String, modifier: Modifier) {
    val state by produceState<SchemaState>(SchemaState.Loading, browser, table) {
        value = SchemaState.Loading
        browser.schema(table).catch { value = SchemaState.Failed }.collect { value = SchemaState.Ready(it) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val current = state) {
            SchemaState.Loading -> Text(stringResource(Res.string.loading))
            SchemaState.Failed -> Text(stringResource(Res.string.database_error))
            is SchemaState.Ready -> when (val schema = current.schema) {
                DatabaseSchema.Unsupported -> Text(stringResource(Res.string.database_schema_unsupported))
                is DatabaseSchema.Available -> {
                    Text(stringResource(Res.string.database_schema_hint), style = MaterialTheme.typography.bodySmall)
                    if (schema.truncated) Text(stringResource(Res.string.database_schema_truncated))
                    Text(stringResource(Res.string.database_column_count, schema.columns.size.toString()),
                        style = MaterialTheme.typography.labelLarge)
                    SelectionContainer(Modifier.weight(1f)) {
                        LazyColumn {
                            items(schema.columns, key = { it.name }) { column ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(column.name, style = MaterialTheme.typography.titleSmall)
                                        Text(column.declaredType.ifEmpty { stringResource(Res.string.database_type_undeclared) },
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (column.primaryKeyPosition > 0) {
                                            Text(stringResource(Res.string.database_primary_key, column.primaryKeyPosition.toString()),
                                                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                        }
                                        Text(stringResource(if (column.notNull) Res.string.database_not_null else Res.string.database_not_null_undeclared),
                                            style = MaterialTheme.typography.bodySmall)
                                        when (column.kind) {
                                            DatabaseColumnKind.Regular -> Unit
                                            DatabaseColumnKind.Hidden -> Text(stringResource(Res.string.database_column_hidden))
                                            DatabaseColumnKind.GeneratedVirtual -> Text(stringResource(Res.string.database_column_virtual))
                                            DatabaseColumnKind.GeneratedStored -> Text(stringResource(Res.string.database_column_stored))
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
