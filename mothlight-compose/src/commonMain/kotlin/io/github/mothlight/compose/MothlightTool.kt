package io.github.mothlight.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Host-owned tool. Only the selected tool is composed and collects its data. */
class MothlightTool(
    val id: String,
    val title: @Composable () -> String,
    val content: @Composable (Modifier) -> Unit,
)

/** Stable ordered tool set. Replacing this object resets panel selection. */
class MothlightTools(tools: List<MothlightTool>) {
    val entries: List<MothlightTool> = tools.toList()
    init {
        require(entries.isNotEmpty()) { "Provide at least one tool" }
        require(entries.all { it.id.isNotBlank() }) { "Tool IDs must not be blank" }
        require(entries.map { it.id }.distinct().size == entries.size) { "Tool IDs must be unique" }
    }
}
