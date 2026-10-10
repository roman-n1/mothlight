package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

data class SqlExecution(val id: Long, val sql: String, val startedAtMillis: Long, val durationMillis: Long, val outcome: SqlOutcome)
data class SqlQueryStat(val sql: String, val executions: Long, val failures: Long, val totalDurationMillis: Long, val lastDurationMillis: Long)
data class SqlTraceSnapshot(val events: List<SqlExecution> = emptyList(), val queries: List<SqlQueryStat> = emptyList())

/** SQL templates only; bind values are never recorded. Hosts must avoid sensitive SQL literals.
 * Query totals survive event eviction; the least recently used query is evicted at queryCapacity.
 */
class SqlTraceRecorder(private val capacity: Int = 200, private val queryCapacity: Int = 100, private val enabled: () -> Boolean = { true }) {
    init { require(capacity > 0); require(queryCapacity > 0) }
    private val sequence = MutableStateFlow(0L)
    private val state = MutableStateFlow(SqlTraceSnapshot())
    val snapshot = state.asStateFlow()
    fun record(sql: String, startedAtMillis: Long, durationMillis: Long, outcome: SqlOutcome = SqlOutcome.Succeeded) {
        if (!enabled() || sql.startsWith(PANEL_MARKER)) return
        val template = sql.take(4096)
        val duration = durationMillis.coerceAtLeast(0)
        val event = SqlExecution(sequence.updateAndGet { it + 1 }, template, startedAtMillis, duration, when (outcome) {
            SqlOutcome.Succeeded -> outcome
            is SqlOutcome.Failed -> SqlOutcome.Failed(outcome.category.take(120))
        })
        state.update { current ->
            val previous = current.queries.firstOrNull { it.sql == template } ?: SqlQueryStat(template, 0, 0, 0, 0)
            val stat = SqlQueryStat(template, previous.executions + 1,
                previous.failures + if (outcome == SqlOutcome.Succeeded) 0 else 1,
                previous.totalDurationMillis + duration, duration)
            SqlTraceSnapshot((current.events + event).takeLast(capacity),
                (current.queries.filterNot { it.sql == template } + stat).takeLast(queryCapacity))
        }
    }
    fun record(sql: String, startedAtMillis: Long, durationMillis: Long, errorType: String) =
        record(sql, startedAtMillis, durationMillis, SqlOutcome.Failed(errorType))
    fun clear() { state.value = SqlTraceSnapshot() }
    companion object { const val PANEL_MARKER = "/* mothlight */ " }
}
