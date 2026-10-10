package io.github.mothlight.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mothlight.*
import io.github.mothlight.compose.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StartupContent(mothlight: Mothlight, modifier: Modifier) {
    val spans by mothlight.startup.spans.collectAsState()
    val rows = remember(spans) { startupRows(spans) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.startup_hint), style = MaterialTheme.typography.bodySmall)
        LazyColumn {
            items(rows, key = { it.id }) { row ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.stage, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.startup_interval, row.offsetMillis, row.durationMillis))
                    LinearProgressIndicator(progress = { row.fraction }, modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider()
            }
        }
    }
}

private enum class LifecycleView { COUNTERS, HISTORY }

@Composable
internal fun LifecycleContent(mothlight: Mothlight, modifier: Modifier) {
    val snapshot by mothlight.lifecycle.snapshot.collectAsState()
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(LifecycleView.COUNTERS) }
    val rows = remember(snapshot, query) { lifecycleRows(snapshot, query) }
    val events = remember(snapshot, query) { lifecycleEvents(snapshot, query) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.lifecycle_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.search_component)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == LifecycleView.COUNTERS, onClick = { mode = LifecycleView.COUNTERS }, label = { Text(stringResource(Res.string.diagnostic_counters)) })
            FilterChip(selected = mode == LifecycleView.HISTORY, onClick = { mode = LifecycleView.HISTORY }, label = { Text(stringResource(Res.string.diagnostic_history)) })
        }
        LazyColumn(Modifier.weight(1f)) {
            when (mode) {
                LifecycleView.COUNTERS -> items(rows, key = { it.kind }) { row ->
                    ListItem(headlineContent = { Text(row.kind) }, supportingContent = {
                        Text(stringResource(Res.string.lifecycle_counts, row.created, row.destroyed, row.balance))
                    })
                    HorizontalDivider()
                }
                LifecycleView.HISTORY -> items(events, key = { it.id }) { event ->
                    ListItem(headlineContent = { Text(event.kind) }, supportingContent = {
                        Text(stringResource(lifecycleLabel(event.event)) + " · " + event.timestampMillis)
                    })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun NavigationContent(mothlight: Mothlight, modifier: Modifier) {
    val entries by mothlight.navigation.entries.collectAsState()
    var query by remember { mutableStateOf("") }
    val rows = remember(entries, query) { navigationRows(entries, query) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.navigation_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.search_destination)) })
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn {
                items(rows, key = { it.id }) { row ->
                    ListItem(headlineContent = { Text(row.destination) }, supportingContent = {
                        Text(stringResource(navigationLabel(row.direction)) + " · " + row.timestampMillis)
                    })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun InvariantsContent(mothlight: Mothlight, modifier: Modifier) {
    val snapshot by mothlight.invariants.snapshot.collectAsState()
    var query by remember { mutableStateOf("") }
    val rows = remember(snapshot, query) { invariantRows(snapshot, query) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.invariants_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.search_rule)) })
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn {
                items(rows, key = { it.rule }) { row ->
                    ListItem(headlineContent = { Text(row.rule, color = severityColor(row.highestSeverity)) }, supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(Res.string.invariant_count, row.count.toString(), row.lastSeenAtMillis.toString()))
                            Text(stringResource(Res.string.invariant_severities, stringResource(severityLabel(row.highestSeverity)), stringResource(severityLabel(row.lastSeverity))))
                            when (val screen = row.lastScreen) {
                                ScreenContext.Unspecified -> Unit
                                is ScreenContext.Named -> Text(stringResource(Res.string.invariant_screen, screen.value))
                            }
                            Text(localizedBody(row.lastSample))
                        }
                    })
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun lifecycleLabel(event: LifecycleEvent) = when (event) {
    LifecycleEvent.CREATED -> Res.string.lifecycle_created
    LifecycleEvent.DESTROYED -> Res.string.lifecycle_destroyed
}
private fun navigationLabel(direction: NavigationDirection) = when (direction) {
    NavigationDirection.PUSH -> Res.string.navigation_push
    NavigationDirection.POP -> Res.string.navigation_pop
    NavigationDirection.REPLACE -> Res.string.navigation_replace
    NavigationDirection.RESET -> Res.string.navigation_reset
}
private fun severityLabel(severity: InvariantSeverity) = when (severity) {
    InvariantSeverity.INFO -> Res.string.severity_info
    InvariantSeverity.WARNING -> Res.string.severity_warning
    InvariantSeverity.ERROR -> Res.string.severity_error
}
@Composable
private fun severityColor(severity: InvariantSeverity) = when (severity) {
    InvariantSeverity.INFO -> MaterialTheme.colorScheme.primary
    InvariantSeverity.WARNING -> MaterialTheme.colorScheme.secondary
    InvariantSeverity.ERROR -> MaterialTheme.colorScheme.error
}
