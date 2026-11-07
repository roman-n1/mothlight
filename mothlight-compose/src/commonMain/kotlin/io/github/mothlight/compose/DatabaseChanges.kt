package io.github.mothlight.compose

import io.github.mothlight.DatabaseCell
import io.github.mothlight.DatabasePage
import io.github.mothlight.DatabaseSchema

internal enum class DatabaseChangeKind { Appeared, Updated, Left }

internal data class DatabaseRowChange(
    val key: List<DatabaseCell>,
    val kind: DatabaseChangeKind,
    val before: List<DatabaseCell>,
    val after: List<DatabaseCell>,
)

internal data class DatabaseDelta(
    val keyColumns: List<String> = emptyList(),
    val changes: List<DatabaseRowChange> = emptyList(),
)

internal fun databaseDelta(previous: DatabasePage, current: DatabasePage, schema: DatabaseSchema): DatabaseDelta {
    if (schema !is DatabaseSchema.Available || schema.truncated) return DatabaseDelta()
    if (previous.columns != current.columns) return DatabaseDelta()
    val names = schema.columns.filter { it.primaryKeyPosition > 0 }
        .sortedBy { it.primaryKeyPosition }.map { it.name }
    val indices = names.map(current.columns::indexOf)
    if (indices.isEmpty() || indices.any { it < 0 }) return DatabaseDelta()

    fun index(page: DatabasePage): Map<List<DatabaseCell>, List<DatabaseCell>> {
        val indexed = mutableMapOf<List<DatabaseCell>, List<DatabaseCell>>()
        for (row in page.rows) {
            if (row.size != page.columns.size) return emptyMap()
            val key = indices.map(row::get)
            // Redacted, missing or truncated values cannot safely identify a row.
            if (key.any { it !is DatabaseCell.Text || it.value == "***" || it.value.length >= 8192 }) return emptyMap()
            if (key in indexed) return emptyMap()
            indexed[key] = row
        }
        return indexed
    }

    val before = index(previous)
    val after = index(current)
    if (before.size != previous.rows.size || after.size != current.rows.size) return DatabaseDelta()
    val changes = buildList {
        for ((key, row) in after) {
            val old = before[key].orEmpty()
            if (old.isEmpty()) add(DatabaseRowChange(key, DatabaseChangeKind.Appeared, emptyList(), row))
            else if (old != row) add(DatabaseRowChange(key, DatabaseChangeKind.Updated, old, row))
        }
        for ((key, row) in before) {
            if (key !in after) add(DatabaseRowChange(key, DatabaseChangeKind.Left, row, emptyList()))
        }
    }
    return DatabaseDelta(names, changes)
}
