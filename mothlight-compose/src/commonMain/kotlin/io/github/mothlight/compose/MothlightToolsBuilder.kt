package io.github.mothlight.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.mothlight.Mothlight

/**
 * Keeps the standard tools and appends host-owned tabs in declaration order.
 * Remember the returned set with its captured dependencies as keys in Compose.
 * Titles are composed for the tab bar; only the selected content is composed.
 */
fun mothlightTools(
    mothlight: Mothlight,
    configure: MothlightToolsBuilder.() -> Unit,
): MothlightTools = MothlightToolsBuilder(defaultMothlightTools(mothlight).entries)
    .apply(configure)
    .build()

/** Registration scope for [mothlightTools]. IDs must be unique, including built-in IDs. */
class MothlightToolsBuilder internal constructor(initialTools: List<MothlightTool>) {
    private val entries = initialTools.toMutableList()

    /** Adds any host composable as a tab. Apply the supplied modifier to its root. */
    fun tab(
        id: String,
        title: @Composable () -> String,
        content: @Composable (Modifier) -> Unit,
    ) {
        tool(MothlightTool(id, title, content))
    }

    /** Adds a ready-made tool, for example a database or retained-object inspector. */
    fun tool(tool: MothlightTool) {
        entries.add(tool)
    }

    internal fun build(): MothlightTools = MothlightTools(entries)
}
