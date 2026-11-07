package io.github.mothlight.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Wraps existing tools in one tab without installing collectors or owning their data.
 * Remember this tool in the host. Selection survives leaving the group; content effects do not.
 * Child IDs are unique within [tools], independently of the surrounding panel.
 */
fun toolGroup(
    id: String,
    title: @Composable () -> String,
    tools: MothlightTools,
    state: MothlightPanelState = MothlightPanelState(),
): MothlightTool = MothlightTool(id, title) { modifier ->
    MothlightToolGroup(tools, modifier, state)
}

/** Only the selected child's content is composed. The host owns tool and data lifetimes. */
@Composable
internal fun MothlightToolGroup(
    tools: MothlightTools,
    modifier: Modifier,
    state: MothlightPanelState,
) {
    val requested by state.selectedTool.collectAsState()
    val selectedId = resolveToolId(tools.entries.map { it.id }, requested)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.entries.forEach { tool ->
                FilterChip(selected = selectedId == tool.id,
                    onClick = { state.selectTool(tool.id) }, label = { Text(tool.title()) })
            }
        }
        HorizontalDivider()
        key(selectedId) {
            tools.entries.first { it.id == selectedId }.content(Modifier.fillMaxWidth().weight(1f))
        }
    }
}
