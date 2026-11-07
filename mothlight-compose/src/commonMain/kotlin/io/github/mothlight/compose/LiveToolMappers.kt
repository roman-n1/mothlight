package io.github.mothlight.compose

import io.github.mothlight.*

internal fun hasPreviousPage(page: DatabasePage) = page.offset > 0
internal fun hasNextPage(page: DatabasePage) = page.offset.toLong() + page.limit < page.totalRows
internal fun previousOffset(page: DatabasePage) = (page.offset - page.limit).coerceAtLeast(0)
internal fun nextOffset(page: DatabasePage) = (page.offset.toLong() + page.limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
internal fun sqlEvents(snapshot: SqlTraceSnapshot, query: String) = snapshot.events.filter { it.sql.contains(query, ignoreCase = true) }.asReversed()
internal enum class SqlSort {
    Count, Total, Last, Errors;

    fun value(stat: SqlQueryStat): Long = when (this) {
        Count -> stat.executions
        Total -> stat.totalDurationMillis
        Last -> stat.lastDurationMillis
        Errors -> stat.failures
    }
}

internal fun sqlStats(snapshot: SqlTraceSnapshot, query: String, sort: SqlSort = SqlSort.Total) =
    snapshot.queries.filter { it.sql.contains(query, ignoreCase = true) }
        .sortedWith(compareByDescending<SqlQueryStat>(sort::value).thenBy { it.sql })


internal fun cellText(cell: DatabaseCell, nullText: String): String = when (cell) {
    DatabaseCell.SqlNull -> nullText
    DatabaseCell.Blob -> "[BLOB]"
    is DatabaseCell.Text -> cell.value
}
