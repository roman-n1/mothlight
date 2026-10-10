package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Bounded, instance-owned event feed adapted from the original Debug panel. */
class ProbeFeed<T : Any>(private val capacity: Int, private val enabled: () -> Boolean = { true }) {
    init { require(capacity > 0) }
    private val mutableItems = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = mutableItems.asStateFlow()
    fun record(item: T) {
        if (!enabled()) return
        mutableItems.update { (it + item).takeLast(capacity) }
    }
    fun clear() { mutableItems.value = emptyList() }
}
