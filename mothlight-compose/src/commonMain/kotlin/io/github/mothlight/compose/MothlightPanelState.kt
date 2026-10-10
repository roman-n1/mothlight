package io.github.mothlight.compose

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Host-owned selection lets an overlay open a tool; panel visibility stays with the host. */
class MothlightPanelState {
    private val selected = MutableStateFlow<String?>(null)
    val selectedTool: StateFlow<String?> = selected
    fun selectTool(id: String) = Unit
}
internal fun resolveToolId(ids: List<String>, requested: String?): String = ids.first()
