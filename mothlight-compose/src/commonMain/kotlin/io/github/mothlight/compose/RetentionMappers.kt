package io.github.mothlight.compose

import io.github.mothlight.retention.*

internal enum class RetentionFilter { ALL, RETAINED, COLLECTED }
internal data class RetentionRow(val id: Long, val label: String, val reason: String, val state: RetentionState, val seconds: String)
internal fun retentionRows(snapshot: RetentionSnapshot, query: String, filter: RetentionFilter): List<RetentionRow> = snapshot.entries
    .filter { entry ->
        val matchesState = when (filter) {
            RetentionFilter.ALL -> true
            RetentionFilter.RETAINED -> entry.state == RetentionState.RETAINED
            RetentionFilter.COLLECTED -> entry.state == RetentionState.COLLECTED
        }
        matchesState && (entry.label.contains(query, ignoreCase = true) || entry.reason.contains(query, ignoreCase = true))
    }
    .sortedWith(compareBy<RetentionEntry> { when (it.state) {
        RetentionState.RETAINED -> 0
        RetentionState.WATCHING -> 1
        RetentionState.COLLECTED -> 2
    } }.thenByDescending { it.ageMillis }.thenBy { it.id })
    .map { RetentionRow(it.id, it.label, it.reason, it.state, "${it.ageMillis / 1000}.${it.ageMillis % 1000 / 100}") }
