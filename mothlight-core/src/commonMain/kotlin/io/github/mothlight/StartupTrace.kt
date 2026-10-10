package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** An interval from this milestone to the next retained milestone; the last interval is zero. */
data class StartupSpan(val id: Long, val stage: String, val startedAtMillis: Long, val durationMillis: Long)

/** Supply timestamps from one clock/origin. No background collector or platform clock is created. */
class StartupTrace(private val capacity: Int = 200, private val enabled: () -> Boolean = { true }) {
    init { require(capacity > 0) }
    private val sequence = MutableStateFlow(0L)
    private val state = MutableStateFlow<List<StartupSpan>>(emptyList())
    val spans = state.asStateFlow()
    fun mark(stage: String, timestampMillis: Long) {
        if (!enabled()) return
        val mark = StartupSpan(sequence.updateAndGet { it + 1 }, stage.take(256), timestampMillis, 0)
        // Milestones and derived intervals are published together: concurrent writers cannot
        // overwrite a newer timeline with durations calculated from an older list.
        state.update { current ->
            val ordered = (current + mark).sortedWith(compareBy<StartupSpan> { it.startedAtMillis }.thenBy { it.id }).takeLast(capacity)
            ordered.mapIndexed { index, span ->
                val next = ordered.getOrElse(index + 1) { span }.startedAtMillis
                val difference = next - span.startedAtMillis
                span.copy(durationMillis = if (difference < 0) Long.MAX_VALUE else difference)
            }
        }
    }
    fun clear() { state.value = emptyList() }
}
