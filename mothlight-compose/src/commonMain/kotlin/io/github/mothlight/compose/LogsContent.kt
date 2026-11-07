package io.github.mothlight.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mothlight.*
import io.github.mothlight.compose.resources.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LogsContent(mothlight: Mothlight, modifier: Modifier) {
    val state = LocalPanelState.current.logs(mothlight.logs)
    val entries by mothlight.logs.items.collectAsState()
    val latestEntries by rememberUpdatedState(entries)
    val rows = remember(entries, state.query, state.level, state.tag) { filteredLogs(entries, state.query, state.level, state.tag) }
    val tags = remember(entries) { entries.map { it.tag }.distinct().sorted() }
    val scope = rememberCoroutineScope()
    var automaticScroll by remember { mutableStateOf(false) }
    val enabled by mothlight.enabled.collectAsState()
    LaunchedEffect(entries, state.following, state.query, state.level, state.tag) {
        state.observe(entries)
        if (state.following && rows.isNotEmpty()) {
            automaticScroll = true
            try { state.list.scrollToItem(0) } finally { automaticScroll = false }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.list.isScrollInProgress && !automaticScroll && (state.list.firstVisibleItemIndex > 0 || state.list.firstVisibleItemScrollOffset > 0) }
            .distinctUntilChanged().collect { reading -> if (reading) state.pause(latestEntries) }
    }
    val pending = if (state.following) 0 else pendingLogs(rows, state.seenIds)
    val resetScroll: () -> Unit = { scope.launch { state.list.scrollToItem(0) } }
    BoxWithConstraints(modifier.testTag("logs-content")) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (wide) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogSearch(state, Modifier.weight(1f), resetScroll)
                LogTags(state, tags, Modifier.width(220.dp), resetScroll)
                LogFollow(state, entries, pending)
            } else LogSearch(state, Modifier.fillMaxWidth(), resetScroll)
            if (wide) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogLevels(state, Modifier.weight(1f), resetScroll)
                LogActions(mothlight, state, rows, entries.size, Modifier.width(420.dp))
            } else {
                LogLevels(state, Modifier.fillMaxWidth(), resetScroll)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogTags(state, tags, Modifier.weight(1f), resetScroll)
                    LogFollow(state, entries, pending)
                }
                LogActions(mothlight, state, rows, entries.size, Modifier.fillMaxWidth())
            }
            Text(stringResource(when {
                state.following -> Res.string.logs_time_hint
                enabled -> Res.string.logs_reading_hint
                else -> Res.string.logs_reading_disabled_hint
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (rows.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(when {
                    entries.isNotEmpty() -> Res.string.logs_no_matches
                    enabled -> Res.string.logs_empty_recording
                    else -> Res.string.logs_empty_disabled
                }), style = MaterialTheme.typography.bodyMedium)
            } else LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("logs-list"), state = state.list) {
                items(rows, key = { it.id }) { entry ->
                    LogItem(entry, state.origin, state.selection == LogSelection.Entry(entry.id)) {
                        val chosen = state.selection == LogSelection.Entry(entry.id)
                        if (!chosen) state.pause(entries)
                        state.selection = if (chosen) LogSelection.Collapsed else LogSelection.Entry(entry.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogSearch(state: LogPanelState, modifier: Modifier, resetScroll: () -> Unit) {
    OutlinedTextField(state.query, { state.query = it; resetScroll() }, modifier.testTag("logs-search"),
        singleLine = true, label = { Text(stringResource(Res.string.logs_search)) })
}

@Composable
private fun LogLevels(state: LogPanelState, modifier: Modifier, resetScroll: () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(state.level == LogLevelFilter.All, { state.level = LogLevelFilter.All; resetScroll() },
            modifier = Modifier.testTag("logs-level-All"), label = { Text(stringResource(Res.string.logs_all_levels)) })
        LogLevel.entries.forEach { level ->
            FilterChip(state.level == LogLevelFilter.Level(level), { state.level = LogLevelFilter.Level(level); resetScroll() },
                modifier = Modifier.testTag("logs-level-${level.name}"), label = { Text(level.name) })
        }
    }
}

@Composable
private fun LogTags(state: LogPanelState, tags: List<String>, modifier: Modifier, resetScroll: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        val label = when (val tag = state.tag) {
            LogTagFilter.All -> stringResource(Res.string.logs_all_tags)
            is LogTagFilter.Tag -> stringResource(Res.string.logs_tag_value, tag.value)
        }
        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().testTag("logs-tags")) {
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(" ▾")
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.logs_all_tags)) }, modifier = Modifier.testTag("logs-tag-All"),
                onClick = { state.tag = LogTagFilter.All; menu = false; resetScroll() })
            tags.forEach { tag ->
                DropdownMenuItem(text = { Text(tag) }, modifier = Modifier.testTag("logs-tag:$tag"),
                    onClick = { state.tag = LogTagFilter.Tag(tag); menu = false; resetScroll() })
            }
        }
    }
}

@Composable
private fun LogFollow(state: LogPanelState, entries: List<LogEntry>, pending: Int) {
    TextButton(onClick = { if (state.following) state.pause(entries) else state.following = true },
        modifier = Modifier.widthIn(max = 190.dp).testTag("logs-follow").semantics {
            toggleableState = if (state.following) ToggleableState.On else ToggleableState.Off
        }) {
        if (pending > 0) Text(stringResource(Res.string.logs_pending, pending.toString()), Modifier.testTag("logs-pending"))
        else Text(stringResource(if (state.following) Res.string.logs_pause_follow else Res.string.logs_resume_follow))
    }
}

@Composable
private fun LogActions(mothlight: Mothlight, state: LogPanelState, rows: List<LogEntry>, total: Int, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.logs_count, rows.size.toString(), total.toString()), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        CopyRecordedText(stringResource(Res.string.logs_copy_filtered), "logs-copy-filtered", rows.isNotEmpty(), rows) { exportLogs(rows) }
        TextButton(onClick = { state.clearView(); mothlight.logs.clear() }, enabled = total > 0, modifier = Modifier.testTag("logs-clear")) { Text(stringResource(Res.string.logs_clear)) }
    }
}

@Composable
private fun LogItem(entry: LogEntry, origin: LogTimeOrigin, expanded: Boolean, toggle: () -> Unit) {
    val description = stringResource(if (expanded) Res.string.logs_expanded else Res.string.logs_collapsed)
    val accent = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = if (expanded) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag("log-row:${entry.id}").clickable(role = Role.Button, onClick = toggle).semantics { stateDescription = description }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when (origin) { LogTimeOrigin.Unset -> "—"; is LogTimeOrigin.At -> relativeLogTime(entry.timestampMillis, origin.millis) },
                    style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.level.name, color = accent, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                Text(entry.tag, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (expanded) "▾" else "▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.logs_original_time, entry.timestampMillis.toString()), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CopyRecordedText(stringResource(Res.string.logs_copy_entry), exportLogs(listOf(entry)), "logs-copy-entry:${entry.id}")
                }
            }
            SelectionContainer {
                Text(entry.message, modifier = Modifier.testTag("log-message:${entry.id}"), style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
