package io.github.mothlight.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import io.github.mothlight.NetworkInspector

internal enum class HttpFilter { All, HttpErrors, TransportErrors, InProgress, Slow }
internal enum class HttpDetailTab { Overview, Request, Response }

/** Retained UI state only; no collectors, scopes or copies of recorded traffic. */
internal class HttpPanelState(val inspector: NetworkInspector) {
    private val jsonViews = mutableMapOf<Pair<String, HttpDetailTab>, JsonBodyState>()
    fun json(id: String, tab: HttpDetailTab) = jsonViews.getOrPut(id to tab) { JsonBodyState() }
    fun retainJson(ids: Set<String>) { jsonViews.keys.removeAll { it.first !in ids } }
    var query by mutableStateOf("")
    var filter by mutableStateOf(HttpFilter.All)
    var selection by mutableStateOf<NetworkSelection>(NetworkSelection.Unselected)
    var detailTab by mutableStateOf(HttpDetailTab.Overview)
    val list = LazyListState()
    val split = MothlightDockState(0.6f)
    var detailScroll by mutableStateOf(ScrollState(0))
    fun select(id: String) {
        selection = NetworkSelection.Request(id)
        detailScroll = ScrollState(0)
    }
}

internal val LocalPanelState = staticCompositionLocalOf { MothlightPanelState() }
