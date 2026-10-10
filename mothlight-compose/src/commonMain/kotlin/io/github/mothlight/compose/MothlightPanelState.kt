package io.github.mothlight.compose

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Host-owned selection lets an overlay open a tool; panel visibility stays with the host. */
class MothlightPanelState {
    private val selected = MutableStateFlow<String?>(null)
    val selectedTool: StateFlow<String?> = selected.asStateFlow()
    fun selectTool(id: String) {
        require(id.isNotBlank())
        selected.value = id
    }
}
internal fun resolveToolId(ids: List<String>, requested: String?): String = requested?.takeIf { it in ids } ?: ids.first()
