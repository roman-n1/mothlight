package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NavigationDirection { PUSH, POP, REPLACE, RESET }
data class NavigationEntry(val id: Long, val destination: String, val direction: NavigationDirection, val timestampMillis: Long)

/** Supply static, sanitized route labels, never serialized navigation arguments. */
class NavigationBreadcrumbs(private val capacity: Int = 200, private val enabled: () -> Boolean = { true }) {
    private val state = MutableStateFlow<List<NavigationEntry>>(emptyList())
    val entries = state.asStateFlow()
    fun record(destination: String, direction: NavigationDirection, timestampMillis: Long) { }
    fun clear() { state.value = emptyList() }
}
