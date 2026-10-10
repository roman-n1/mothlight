package io.github.mothlight.sqlite

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.mothlight.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SQLiteToolsTest {
    @Test fun readerBoundsPagesMasksColumnsAndNeverExecutesInjectedNames() = runTest {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.prepare("CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT, token TEXT, data BLOB)").use { it.step() }
            connection.prepare("INSERT INTO items VALUES (1, 'one', 'secret', X'1234'), (2, 'two', 'hidden', NULL), (3, NULL, NULL, NULL)").use { it.step() }
            val reader = SQLiteDatabaseSource(SqlReadExecutor { sql, maxRows ->
                connection.prepare(sql).use { readResult(it, maxRows) { it.step() } }
            }, { flowOf(Unit) })
            assertEquals(listOf("items"), reader.tables())
            val page = reader.page("items", 1, 1)
            assertEquals(3L, page.totalRows)
            assertEquals(listOf("2", "two", "***", null), page.rows.single())
            assertEquals(listOf("id", "name", "token", "data"), page.columns)
            assertEquals("[BLOB]", reader.page("items", 1, 0).rows.single().last())
            assertNull(reader.page("items", 1, 2).rows.single()[1])
            assertEquals(200, reader.page("items", 99999, 0).limit)
            assertFailsWith<IllegalArgumentException> { reader.page("items; DROP TABLE items", 10, 0) }
            assertEquals(3, reader.page("items", 50, 0).rows.size)
            assertFailsWith<IllegalArgumentException> { reader.page("items", 0, 0) }
            assertFailsWith<IllegalArgumentException> { reader.page("items", 10, -1) }
        } finally { connection.close() }
    }
    @Test fun tracingPreservesResultsExceptionsAndPreparedStatementReuseWithoutBindValues() {
        var enabled = false
        val trace = SqlTraceRecorder(enabled = { enabled })
        var time = 0L
        val driver = TracingSQLiteDriver(BundledSQLiteDriver(), trace, { enabled }) { time }
        val connection = driver.open(":memory:")
        try {
            connection.prepare("CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT)").use { it.step() }
            assertTrue(trace.snapshot.value.events.isEmpty())
            enabled = true
            val statement = connection.prepare("INSERT INTO items VALUES (?, ?)")
            statement.bindLong(1, 1); statement.bindText(2, "secret-one")
            statement.step(); time = 5; statement.reset()
            statement.clearBindings(); statement.bindLong(1, 2); statement.bindText(2, "secret-two")
            statement.step(); time = 9; statement.close()
            assertEquals(listOf(5L, 4L), trace.snapshot.value.events.map { it.durationMillis })
            assertFalse(trace.snapshot.value.toString().contains("secret"))
            connection.prepare("SELECT COUNT(*) FROM items").use { assertTrue(it.step()); assertEquals(2L, it.getLong(0)) }
            assertFails { connection.prepare("INSERT INTO items VALUES (1, NULL)").use { it.step() } }
            assertNotNull(trace.snapshot.value.events.last().errorType)
            connection.prepare(SqlTraceRecorder.PANEL_MARKER + "SELECT * FROM items").use { it.step() }
            assertEquals(4, trace.snapshot.value.events.size)
        } finally { connection.close() }
    }
}
