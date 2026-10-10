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

private enum class SqlView { EVENTS, STATISTICS }

@Composable
internal fun SqlContent(mothlight: Mothlight, modifier: Modifier) {
    val snapshot by mothlight.sql.snapshot.collectAsState()
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SqlView.EVENTS) }
    val events = remember(snapshot, query) { sqlEvents(snapshot, query) }
    val stats = remember(snapshot, query) { sqlStats(snapshot, query) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.sql_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.search_sql)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == SqlView.EVENTS, onClick = { mode = SqlView.EVENTS }, label = { Text(stringResource(Res.string.sql_executions)) })
            FilterChip(selected = mode == SqlView.STATISTICS, onClick = { mode = SqlView.STATISTICS }, label = { Text(stringResource(Res.string.sql_statistics)) })
        }
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn {
                when (mode) {
                    SqlView.EVENTS -> items(events, key = { it.id }) { event ->
                        ListItem(headlineContent = { Text(event.sql) }, supportingContent = {
                            Column {
                                Text(stringResource(Res.string.duration_ms, event.durationMillis.toString()))
                                event.errorType?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        })
                        HorizontalDivider()
                    }
                    SqlView.STATISTICS -> items(stats, key = { it.sql }) { stat ->
                        ListItem(headlineContent = { Text(stat.sql) }, supportingContent = {
                            Text(stringResource(Res.string.sql_stats, stat.executions.toString(), stat.totalDurationMillis.toString(), stat.failures.toString()))
                        })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun RealtimeContent(mothlight: Mothlight, modifier: Modifier) {
    val frames by mothlight.realtime.frames.collectAsState()
    val connections by mothlight.realtime.connections.collectAsState()
    var query by remember { mutableStateOf("") }
    val rows = remember(frames, query) { realtimeFrames(frames, query) }
    val states = remember(connections, query) { connectionEvents(connections, query) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.search_channel)) })
        Text(stringResource(Res.string.connection_history), style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.weight(1f)) {
            items(states, key = { it.id }) { event ->
                Text(event.channel + " · " + stringResource(connectionLabel(event.state)) + " · " + event.timestampMillis, Modifier.padding(4.dp))
            }
        }
        Text(stringResource(Res.string.realtime_frames), style = MaterialTheme.typography.titleSmall)
        SelectionContainer(Modifier.weight(2f)) {
            LazyColumn {
                items(rows, key = { it.id }) { frame ->
                    ListItem(headlineContent = { Text(localizedBody(frame.payload)) }, supportingContent = {
                        Text(frame.channel + " · " + stringResource(directionLabel(frame.direction)) + " · " + frame.timestampMillis)
                    })
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun directionLabel(direction: FrameDirection) = when (direction) {
    FrameDirection.INCOMING -> Res.string.incoming
    FrameDirection.OUTGOING -> Res.string.outgoing
}
private fun connectionLabel(state: ConnectionState) = when (state) {
    ConnectionState.CONNECTING -> Res.string.connecting
    ConnectionState.CONNECTED -> Res.string.connected
    ConnectionState.DISCONNECTED -> Res.string.disconnected
    ConnectionState.RECONNECTING -> Res.string.reconnecting
    ConnectionState.FAILED -> Res.string.connection_failed
}
