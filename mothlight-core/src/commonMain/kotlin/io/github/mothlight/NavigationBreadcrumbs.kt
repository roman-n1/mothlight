package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

enum class NavigationDirection { PUSH, POP, REPLACE, RESET }
data class NavigationEntry(val id: Long, val destination: String, val direction: NavigationDirection, val timestampMillis: Long)

/** Supply static, sanitized route labels, never serialized navigation arguments. */
class NavigationBreadcrumbs(private val capacity: Int = 200, private val enabled: () -> Boolean = { true }) {
    init { require(capacity > 0) }
    private val sequence = MutableStateFlow(0L)
    private val state = MutableStateFlow<List<NavigationEntry>>(emptyList())
    val entries = state.asStateFlow()
    fun record(destination: String, direction: NavigationDirection, timestampMillis: Long) {
        if (!enabled()) return
        val entry = NavigationEntry(sequence.updateAndGet { it + 1 }, destination.take(256), direction, timestampMillis)
        state.update { (it + entry).takeLast(capacity) }
    }
    fun clear() { state.value = emptyList() }
}
