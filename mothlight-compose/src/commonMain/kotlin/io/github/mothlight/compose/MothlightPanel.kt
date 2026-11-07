package io.github.mothlight.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import io.github.mothlight.Mothlight
import io.github.mothlight.compose.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Embeddable panel that inherits the host's MaterialTheme colors, typography and shapes.
 * Visibility belongs to the host, recording belongs to [mothlight].
 */
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
    var menuVisible by remember { mutableStateOf(false) }
    Surface(modifier, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Mothlight", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                TextButton(onClick = { mothlight.setEnabled(toggleRecording(enabled)) }, modifier = Modifier.testTag("panel-record")) {
                    Text(stringResource(recordingAction(enabled)))
                }
                Box {
                    val menuLabel = stringResource(Res.string.panel_options)
                    IconButton(onClick = { menuVisible = true }, modifier = Modifier.testTag("panel-options").semantics { contentDescription = menuLabel }) { Text("⋯") }
                    DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                        DropdownMenuItem(text = { Text(memoryLabel) },
                            trailingIcon = { if (memoryVisible) Text("✓") },
                            modifier = Modifier.testTag("panel-memory").semantics {
                                role = Role.Checkbox
                                toggleableState = if (memoryVisible) ToggleableState.On else ToggleableState.Off
                            },
                            onClick = { mothlight.memoryOverlay.setEnabled(!memoryVisible); menuVisible = false })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.clear_all)) },
                            modifier = Modifier.testTag("panel-clear-all"), onClick = { mothlight.clear(); menuVisible = false })
                    }
                }
            }
            val toolTabs: @Composable () -> Unit = {
                tools.entries.forEach { tool ->
                    FilterChip(selected = selectedId == tool.id, onClick = { state.selectTool(tool.id) },
                        label = { Text(tool.title()) })
                }
            }
            if (tools.entries.size <= 5) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { toolTabs() }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    toolTabs()
                }
            }
            HorizontalDivider()
            key(selectedId) {
                CompositionLocalProvider(LocalPanelState provides state) {
                    tools.entries.first { it.id == selectedId }.content(Modifier.fillMaxWidth().weight(1f))
                }
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
    MothlightTool("startup", { stringResource(Res.string.startup) }) { StartupContent(mothlight, it) },
    MothlightTool("logs", { stringResource(Res.string.logs) }) { LogsContent(mothlight, it) },
))

@Preview
@Composable
private fun PanelPreview() {
    MaterialTheme { MothlightPanel(remember { Mothlight() }, Modifier.size(720.dp, 480.dp)) }
}
