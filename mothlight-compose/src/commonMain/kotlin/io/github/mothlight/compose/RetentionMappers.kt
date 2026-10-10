package io.github.mothlight.compose

import io.github.mothlight.retention.*

internal enum class RetentionFilter { ALL, RETAINED, COLLECTED }
internal data class RetentionRow(val id: Long, val label: String, val reason: String, val state: RetentionState, val seconds: String)
internal fun retentionRows(snapshot: RetentionSnapshot, query: String, filter: RetentionFilter): List<RetentionRow> = emptyList()
