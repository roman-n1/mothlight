package io.github.mothlight.compose

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Host-owned selection lets an overlay open a tool; panel visibility stays with the host. */
class MothlightPanelState {
    private var logBinding: LogStateBinding = LogStateBinding.Unbound
    internal fun logs(feed: io.github.mothlight.ProbeFeed<io.github.mothlight.LogEntry>): LogPanelState {
        val current = logBinding
        if (current is LogStateBinding.Bound && current.state.feed === feed) return current.state
        return LogPanelState(feed).also { logBinding = LogStateBinding.Bound(it) }
    }
    private var httpBinding: HttpStateBinding = HttpStateBinding.Unbound
    internal fun http(inspector: io.github.mothlight.NetworkInspector): HttpPanelState {
        val current = httpBinding
        if (current is HttpStateBinding.Bound && current.state.inspector === inspector) return current.state
        return HttpPanelState(inspector).also { httpBinding = HttpStateBinding.Bound(it) }
    }
    private val selected = MutableStateFlow<ToolSelection>(ToolSelection.Default)
    val selectedTool: StateFlow<ToolSelection> = selected.asStateFlow()
    fun selectTool(id: String) {
        require(id.isNotBlank())
        selected.value = ToolSelection.Named(id)
    }
}

private sealed interface HttpStateBinding {
    data object Unbound : HttpStateBinding
    data class Bound(val state: HttpPanelState) : HttpStateBinding
}
private sealed interface LogStateBinding {
    data object Unbound : LogStateBinding
    data class Bound(val state: LogPanelState) : LogStateBinding
}
internal fun resolveToolId(ids: List<String>, requested: ToolSelection): String = when (requested) {
    ToolSelection.Default -> ids.first()
    is ToolSelection.Named -> if (requested.id in ids) requested.id else ids.first()
}
