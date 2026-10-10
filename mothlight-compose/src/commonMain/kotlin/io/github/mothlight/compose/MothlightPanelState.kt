package io.github.mothlight.compose

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Host-owned selection lets an overlay open a tool; panel visibility stays with the host. */
class MothlightPanelState {
    private val selected = MutableStateFlow<ToolSelection>(ToolSelection.Default)
    val selectedTool: StateFlow<ToolSelection> = selected.asStateFlow()
    fun selectTool(id: String) {
        require(id.isNotBlank())
        selected.value = ToolSelection.Named(id)
    }
}
internal fun resolveToolId(ids: List<String>, requested: ToolSelection): String = when (requested) {
    ToolSelection.Default -> ids.first()
    is ToolSelection.Named -> if (requested.id in ids) requested.id else ids.first()
}
