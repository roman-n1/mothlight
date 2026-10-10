package io.github.mothlight.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mothlight.compose.resources.*
import io.github.mothlight.retention.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Optional tool: the host owns the watcher's scope and reports lifetime boundaries. */
fun retentionTool(watcher: RetentionWatcher, id: String = "retention") = MothlightTool(
    id, { stringResource(Res.string.retention_title) }, { RetentionContent(watcher, it) },
)

@Composable
private fun RetentionContent(watcher: RetentionWatcher, modifier: Modifier) {
    val snapshot by watcher.snapshots.collectAsState(watcher.snapshot)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RetentionFilter.ALL) }
    val rows = remember(snapshot, query, filter) { retentionRows(snapshot, query, filter) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(supportLabel(snapshot.support)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(Res.string.retention_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(Res.string.retention_count, snapshot.retainedCount))
        Text(stringResource(modeLabel(snapshot.mode)))
        Text(stringResource(checkLabel(snapshot.lastCheck)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(Res.string.retention_evicted, snapshot.evictedCount), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { watcher.setEnabled(snapshot.mode == WatcherMode.DISABLED) }, enabled = canToggle(snapshot.mode)) {
                Text(stringResource(toggleLabel(snapshot.mode)))
            }
            TextButton(onClick = watcher::clear) { Text(stringResource(Res.string.clear_all)) }
            TextButton(onClick = { scope.launch { watcher.checkNow() } }, enabled = snapshot.mode == WatcherMode.RUNNING) {
                Text(stringResource(Res.string.retention_recheck))
            }
            TextButton(onClick = { scope.launch { watcher.checkNow(requestCollection = true) } }, enabled = snapshot.mode == WatcherMode.RUNNING) {
                Text(stringResource(Res.string.retention_gc))
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.retention_search)) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RetentionFilter.entries.forEach { choice ->
                FilterChip(selected = filter == choice, onClick = { filter = choice }, label = { Text(stringResource(filterLabel(choice))) })
            }
        }
        if (rows.isEmpty()) Text(stringResource(Res.string.retention_empty))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.label, style = MaterialTheme.typography.titleSmall)
                        Text(row.reason, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(Res.string.retention_row, stringResource(stateLabel(row.state)), row.seconds))
                    }
                }
            }
        }
    }
}

@Composable
internal fun RetentionBadge(watcher: RetentionWatcher, onOpen: (() -> Unit)?) {
    val snapshot by watcher.snapshots.collectAsState(watcher.snapshot)
    val label = when (snapshot.mode) {
        WatcherMode.RUNNING -> stringResource(Res.string.retention_count, snapshot.retainedCount)
        else -> stringResource(modeLabel(snapshot.mode))
    }
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        if (onOpen == null) Text(label, Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
        else TextButton(onClick = onOpen) { Text(label) }
    }
}

private fun canToggle(mode: WatcherMode) = mode == WatcherMode.DISABLED || mode == WatcherMode.RUNNING
private fun toggleLabel(mode: WatcherMode) = if (mode == WatcherMode.RUNNING) Res.string.pause else Res.string.retention_start
private fun supportLabel(support: RetentionSupport) = when (support) {
    RetentionSupport.MANAGED_HEAP -> Res.string.retention_managed
    RetentionSupport.KOTLIN_NATIVE -> Res.string.retention_native
    RetentionSupport.UNSUPPORTED -> Res.string.retention_unsupported
}
private fun modeLabel(mode: WatcherMode) = when (mode) {
    WatcherMode.DISABLED -> Res.string.retention_disabled
    WatcherMode.RUNNING -> Res.string.retention_running
    WatcherMode.UNSUPPORTED -> Res.string.retention_unsupported
    WatcherMode.CLOSED -> Res.string.retention_closed
}
private fun checkLabel(check: RetentionCheck) = when (check) {
    RetentionCheck.NOT_CHECKED -> Res.string.retention_not_checked
    RetentionCheck.OBSERVED -> Res.string.retention_observed
    RetentionCheck.COLLECTION_REQUESTED -> Res.string.retention_requested
    RetentionCheck.FAILED -> Res.string.retention_failed
}
private fun filterLabel(filter: RetentionFilter) = when (filter) {
    RetentionFilter.ALL -> Res.string.retention_all
    RetentionFilter.RETAINED -> Res.string.retention_retained
    RetentionFilter.COLLECTED -> Res.string.retention_collected
}
private fun stateLabel(state: RetentionState) = when (state) {
    RetentionState.WATCHING -> Res.string.retention_watching
    RetentionState.RETAINED -> Res.string.retention_retained
    RetentionState.COLLECTED -> Res.string.retention_collected
}
