package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LifecycleEvent { CREATED, DESTROYED }
data class LifecycleEntry(val id: Long, val kind: String, val event: LifecycleEvent, val timestampMillis: Long)
data class LifecycleCount(val kind: String, val created: Long, val destroyed: Long)
data class LifecycleSnapshot(val events: List<LifecycleEntry> = emptyList(), val counters: List<LifecycleCount> = emptyList())

/** Counts describe observed events, not live instances or proof of leaks. */
class LifecycleRecorder(private val capacity: Int = 200, private val kindCapacity: Int = 100, private val enabled: () -> Boolean = { true }) {
    private val state = MutableStateFlow(LifecycleSnapshot())
    val snapshot = state.asStateFlow()
    fun record(kind: String, event: LifecycleEvent, timestampMillis: Long) { }
    fun clear() { state.value = LifecycleSnapshot() }
}
