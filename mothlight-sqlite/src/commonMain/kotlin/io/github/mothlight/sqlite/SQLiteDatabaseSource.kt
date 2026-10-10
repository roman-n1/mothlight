package io.github.mothlight.sqlite

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteStatement
import io.github.mothlight.*
import kotlinx.coroutines.flow.Flow

data class SqlResult(val columns: List<String>, val rows: List<List<String?>>)

/** Executes the generated read on a host-owned connection and dispatcher. */
fun interface SqlReadExecutor { suspend fun read(sql: String, maxRows: Int): SqlResult }

/** Bounded table browser. No raw query or mutation entry point. */
class SQLiteDatabaseSource(
    private val executor: SqlReadExecutor,
    private val invalidations: () -> Flow<Unit>,
    private val redactor: Redactor = Redactor(),
) : DatabaseSource {
    override suspend fun tables(): List<String> = read(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT GLOB 'sqlite_*' " +
            "AND name NOT GLOB 'room_*' AND name NOT GLOB 'android_*' ORDER BY name", 1024,
    ).rows.mapNotNull { it.firstOrNull() }

    override suspend fun page(table: String, limit: Int, offset: Int): DatabasePage {
        require(limit > 0); require(offset >= 0)
        require(table in tables()) { "Unknown table" }
        val boundedLimit = limit.coerceAtMost(200)
        val identifier = "\"" + table.replace("\"", "\"\"") + "\""
        val count = read("SELECT COUNT(*) FROM $identifier", 1).rows.single().single()!!.toLong()
        val columns = read("SELECT * FROM $identifier LIMIT 0", 0).columns
        val order = columns.indices.joinToString(",") { (it + 1).toString() }
        val result = read("SELECT * FROM $identifier ORDER BY $order LIMIT $boundedLimit OFFSET $offset", boundedLimit)
        val safeRows = result.rows.map { row ->
            row.mapIndexed { index, value ->
                value?.let { redactor.headers(mapOf(result.columns[index] to it)).values.single() }
            }
        }
        return DatabasePage(table, result.columns, safeRows, count, offset, boundedLimit)
    }
    override fun changes() = invalidations()
    private suspend fun read(sql: String, maxRows: Int) = executor.read(SqlTraceRecorder.PANEL_MARKER + sql, maxRows)
}

/** Shared by synchronous Room 2 and suspending Room 3 reader blocks. Does not close the statement. */
inline fun readResult(statement: SQLiteStatement, maxRows: Int, step: () -> Boolean): SqlResult {
    require(maxRows >= 0)
    val columns = List(statement.getColumnCount()) { statement.getColumnName(it) }
    val rows = mutableListOf<List<String?>>()
    while (rows.size < maxRows.coerceAtMost(1024) && step()) {
        rows += columns.indices.map { index ->
            when (statement.getColumnType(index)) {
                SQLITE_DATA_NULL -> null
                SQLITE_DATA_BLOB -> "[BLOB]"
                else -> statement.getText(index).take(8192)
            }
        }
    }
    return SqlResult(columns, rows)
}
