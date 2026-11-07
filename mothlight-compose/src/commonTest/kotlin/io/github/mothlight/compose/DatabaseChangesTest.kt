package io.github.mothlight.compose

import io.github.mothlight.DatabaseCell
import io.github.mothlight.DatabaseColumn
import io.github.mothlight.DatabasePage
import io.github.mothlight.DatabaseSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseChangesTest {
    private val columns = listOf("id", "tenant", "title")
    private val schema = DatabaseSchema.Available(listOf(
        DatabaseColumn("id", "INTEGER", true, 2),
        DatabaseColumn("tenant", "TEXT", true, 1),
        DatabaseColumn("title", "TEXT", false, 0),
    ))

    @Test fun comparesCompositeKeysEvenWhenRowsMove() {
        val old = row("1", "a", "old")
        val updated = row("1", "a", "new")
        val unchanged = row("1", "b", "same")
        val left = row("2", "a", "left")
        val appeared = row("3", "a", "appeared")
        val delta = databaseDelta(page(old, unchanged, left), page(unchanged, updated, appeared), schema)

        assertEquals(listOf("tenant", "id"), delta.keyColumns)
        assertEquals(listOf(
            DatabaseRowChange(listOf(old[1], old[0]), DatabaseChangeKind.Updated, old, updated),
            DatabaseRowChange(listOf(appeared[1], appeared[0]), DatabaseChangeKind.Appeared, emptyList(), appeared),
            DatabaseRowChange(listOf(left[1], left[0]), DatabaseChangeKind.Left, left, emptyList()),
        ), delta.changes)
    }

    @Test fun declinesAmbiguousOrRedactedKeys() {
        val first = row("1", "a", "old")
        val duplicate = row("1", "a", "duplicate")
        val masked = row("1", "***", "masked")
        assertTrue(databaseDelta(page(first, duplicate), page(first), schema).keyColumns.isEmpty())
        assertTrue(databaseDelta(page(masked), page(first), schema).keyColumns.isEmpty())
    }

    private fun row(vararg cells: String) = cells.map { DatabaseCell.Text(it) }

    private fun page(vararg rows: List<DatabaseCell>) =
        DatabasePage("items", columns, rows.toList(), rows.size.toLong(), 0, 50)
}
