package io.github.mothlight.compose

import io.github.mothlight.*

internal fun databaseRows(page: DatabasePage, nullText: String): List<String> = page.rows.map { values ->
    page.columns.mapIndexed { index, column -> "$column: ${values.getOrNull(index) ?: nullText}" }.joinToString("\n")
}
internal fun hasPreviousPage(page: DatabasePage) = page.offset > 0
internal fun hasNextPage(page: DatabasePage) = page.offset.toLong() + page.limit < page.totalRows
internal fun previousOffset(page: DatabasePage) = (page.offset - page.limit).coerceAtLeast(0)
internal fun nextOffset(page: DatabasePage) = (page.offset.toLong() + page.limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
internal fun sqlEvents(snapshot: SqlTraceSnapshot, query: String) = snapshot.events.filter { it.sql.contains(query, ignoreCase = true) }.asReversed()
internal fun sqlStats(snapshot: SqlTraceSnapshot, query: String) = snapshot.queries.filter { it.sql.contains(query, ignoreCase = true) }.sortedByDescending { it.totalDurationMillis }
internal fun realtimeFrames(frames: List<RealtimeFrame>, query: String) = frames.filter { it.channel.contains(query, ignoreCase = true) }.asReversed()
internal fun connectionEvents(events: List<ConnectionEvent>, query: String) = events.filter { it.channel.contains(query, ignoreCase = true) }.asReversed()
