package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An interval from this milestone to the next retained milestone; the last interval is zero. */
data class StartupSpan(val id: Long, val stage: String, val startedAtMillis: Long, val durationMillis: Long)

/** Supply timestamps from one clock/origin. No background collector or platform clock is created. */
class StartupTrace(private val capacity: Int = 200, private val enabled: () -> Boolean = { true }) {
    private val state = MutableStateFlow<List<StartupSpan>>(emptyList())
    val spans = state.asStateFlow()
    fun mark(stage: String, timestampMillis: Long) { }
    fun clear() { state.value = emptyList() }
}
