package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SqlExecution(val id: Long, val sql: String, val startedAtMillis: Long, val durationMillis: Long, val errorType: String?)
data class SqlQueryStat(val sql: String, val executions: Long, val failures: Long, val totalDurationMillis: Long, val lastDurationMillis: Long)
data class SqlTraceSnapshot(val events: List<SqlExecution> = emptyList(), val queries: List<SqlQueryStat> = emptyList())

/** SQL templates only; bind values are never recorded. Hosts must avoid sensitive SQL literals. */
class SqlTraceRecorder(private val capacity: Int = 200, private val queryCapacity: Int = 100, private val enabled: () -> Boolean = { true }) {
    private val state = MutableStateFlow(SqlTraceSnapshot())
    val snapshot = state.asStateFlow()
    fun record(sql: String, startedAtMillis: Long, durationMillis: Long, errorType: String? = null) { }
    fun clear() { state.value = SqlTraceSnapshot() }
    companion object { const val PANEL_MARKER = "/* mothlight */ " }
}
