package io.github.mothlight.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import io.github.mothlight.Mothlight
import io.github.mothlight.compose.resources.*
import org.jetbrains.compose.resources.stringResource

/** Embeddable panel; visibility belongs to the host, recording belongs to [mothlight]. */
@Composable
fun MothlightPanel(
    mothlight: Mothlight,
    modifier: Modifier = Modifier,
    tools: MothlightTools = remember(mothlight) { defaultMothlightTools(mothlight) },
    state: MothlightPanelState = remember(tools) { MothlightPanelState() },
) {
    val requestedId by state.selectedTool.collectAsState()
    val selectedId = resolveToolId(tools.entries.map { it.id }, requestedId)
    val enabled by mothlight.enabled.collectAsState()
    val memoryVisible by mothlight.memoryOverlay.enabled.collectAsState()
    val memoryLabel = stringResource(Res.string.show_memory_usage)
    Surface(modifier) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text("Mothlight", style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = { mothlight.setEnabled(toggleRecording(enabled)) }) {
                        Text(stringResource(recordingAction(enabled)))
                    }
                    TextButton(onClick = mothlight::clear) { Text(stringResource(Res.string.clear_all)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(memoryLabel, style = MaterialTheme.typography.bodyMedium)
                Switch(checked = memoryVisible, onCheckedChange = mothlight.memoryOverlay::setEnabled,
                    modifier = Modifier.semantics { contentDescription = memoryLabel })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tools.entries.forEach { tool ->
                    FilterChip(selected = selectedId == tool.id, onClick = { state.selectTool(tool.id) }, label = { Text(tool.title()) })
                }
            }
            HorizontalDivider()
            key(selectedId) {
                tools.entries.first { it.id == selectedId }.content(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

private fun toggleRecording(current: Boolean): Boolean = !current
private fun recordingAction(enabled: Boolean) = if (enabled) Res.string.pause else Res.string.record

/** Use MaterialTheme in the host to customize colors and typography. */
fun defaultMothlightTools(mothlight: Mothlight): MothlightTools = MothlightTools(listOf(
    MothlightTool("http", { stringResource(Res.string.http) }) { NetworkContent(mothlight, it) },
    MothlightTool("sql", { stringResource(Res.string.sql) }) { SqlContent(mothlight, it) },
    MothlightTool("realtime", { stringResource(Res.string.realtime) }) { RealtimeContent(mothlight, it) },
    MothlightTool("startup", { stringResource(Res.string.startup) }) { StartupContent(mothlight, it) },
    MothlightTool("lifecycle", { stringResource(Res.string.lifecycle) }) { LifecycleContent(mothlight, it) },
    MothlightTool("navigation", { stringResource(Res.string.navigation) }) { NavigationContent(mothlight, it) },
    MothlightTool("invariants", { stringResource(Res.string.invariants) }) { InvariantsContent(mothlight, it) },
    MothlightTool("logs", { stringResource(Res.string.logs) }) { LogsContent(mothlight, it) },
    MothlightTool("events", { stringResource(Res.string.events) }) { EventsContent(mothlight, it) },
))

@Preview
@Composable
private fun PanelPreview() {
    MaterialTheme { MothlightPanel(remember { Mothlight() }, Modifier.size(720.dp, 480.dp)) }
}
