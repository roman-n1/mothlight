package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

enum class LifecycleEvent { CREATED, DESTROYED }
data class LifecycleEntry(val id: Long, val kind: String, val event: LifecycleEvent, val timestampMillis: Long)
data class LifecycleCount(val kind: String, val created: Long, val destroyed: Long)
data class LifecycleSnapshot(val events: List<LifecycleEntry> = emptyList(), val counters: List<LifecycleCount> = emptyList())

/** Counts describe observed events, not live instances or proof of leaks. */
class LifecycleRecorder(private val capacity: Int = 200, private val kindCapacity: Int = 100, private val enabled: () -> Boolean = { true }) {
    init { require(capacity > 0); require(kindCapacity > 0) }
    private val sequence = MutableStateFlow(0L)
    private val state = MutableStateFlow(LifecycleSnapshot())
    val snapshot = state.asStateFlow()
    fun record(kind: String, event: LifecycleEvent, timestampMillis: Long) {
        if (!enabled()) return
        val label = kind.take(256)
        val entry = LifecycleEntry(sequence.updateAndGet { it + 1 }, label, event, timestampMillis)
        state.update { current ->
            val previous = current.counters.firstOrNull { it.kind == label } ?: LifecycleCount(label, 0, 0)
            val next = when (event) {
                LifecycleEvent.CREATED -> previous.copy(created = previous.created + 1)
                LifecycleEvent.DESTROYED -> previous.copy(destroyed = previous.destroyed + 1)
            }
            LifecycleSnapshot((current.events + entry).takeLast(capacity),
                (current.counters.filterNot { it.kind == label } + next).takeLast(kindCapacity))
        }
    }
    fun clear() { state.value = LifecycleSnapshot() }
}
