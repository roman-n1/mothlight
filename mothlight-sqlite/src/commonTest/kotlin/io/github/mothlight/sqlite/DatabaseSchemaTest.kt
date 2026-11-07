package io.github.mothlight.sqlite

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.mothlight.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DatabaseSchemaTest {
    @Test fun readsDeclaredConstraintsCompositeKeysAndGeneratedColumnsWithoutDefaults() = runTest {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.prepare("CREATE TABLE items (a TEXT, b INTEGER NOT NULL, token TEXT DEFAULT 'private-default', loose, virtual TEXT GENERATED ALWAYS AS (a || 'private-expression') VIRTUAL, stored INTEGER GENERATED ALWAYS AS (b * 2) STORED, PRIMARY KEY (b, a))").use { it.step() }
            val statements = mutableListOf<String>()
            val source = SQLiteDatabaseSource(SqlReadExecutor { sql, maxRows ->
                statements += sql
                connection.prepare(sql).use { readResult(it, maxRows) { it.step() } }
            }, { flowOf(Unit) })
            val schema = assertIs<DatabaseSchema.Available>(source.schema("items"))
            assertEquals(listOf("a", "b", "token", "loose", "virtual", "stored"), schema.columns.map { it.name })
            assertEquals(listOf(2, 1, 0, 0, 0, 0), schema.columns.map { it.primaryKeyPosition })
            assertFalse(schema.columns[0].notNull) // SQLite reports the declared flag even for a PK.
            assertTrue(schema.columns[1].notNull)
            assertEquals("", schema.columns[3].declaredType)
            assertEquals(DatabaseColumnKind.GeneratedVirtual, schema.columns[4].kind)
            assertEquals(DatabaseColumnKind.GeneratedStored, schema.columns[5].kind)
            assertFalse(schema.truncated)
            assertFalse(schema.toString().contains("private"))
            assertFalse(statements.any { it.contains("dflt_value") || it.contains("SELECT *") })
            assertTrue(statements.all { it.startsWith(SqlTraceRecorder.PANEL_MARKER) })
        } finally { connection.close() }
    }

    @Test fun validatesNamesEscapesQuotesAndBoundsWideSchemas() = runTest {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            val name = "odd'\"; DROP TABLE wide;--"
            val identifier = "\"" + name.replace("\"", "\"\"") + "\""
            connection.prepare("CREATE TABLE $identifier (id INTEGER)").use { it.step() }
            connection.prepare("CREATE TABLE wide (${(0..512).joinToString { "c$it TEXT" }})").use { it.step() }
            val source = SQLiteDatabaseSource(SqlReadExecutor { sql, maxRows ->
                connection.prepare(sql).use { readResult(it, maxRows) { it.step() } }
            }, { flowOf(Unit) })
            assertEquals("id", assertIs<DatabaseSchema.Available>(source.schema(name)).columns.single().name)
            assertFailsWith<IllegalArgumentException> { source.schema("wide; DROP TABLE wide") }
            val wide = assertIs<DatabaseSchema.Available>(source.schema("wide"))
            assertEquals(512, wide.columns.size)
            assertTrue(wide.truncated)
            assertEquals("c511", wide.columns.last().name)
        } finally { connection.close() }
    }
}
