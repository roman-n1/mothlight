package io.github.mothlight.sqlite

import androidx.sqlite.SQLiteStatement
import io.github.mothlight.*
import kotlinx.coroutines.flow.Flow

data class SqlResult(val columns: List<String>, val rows: List<List<String?>>)
fun interface SqlReadExecutor { suspend fun read(sql: String, maxRows: Int): SqlResult }
class SQLiteDatabaseSource(private val executor: SqlReadExecutor, private val invalidations: () -> Flow<Unit>, private val redactor: Redactor = Redactor()) : DatabaseSource {
    override suspend fun tables(): List<String> = emptyList()
    override suspend fun page(table: String, limit: Int, offset: Int) = DatabasePage(table, emptyList(), emptyList(), 0, offset, limit)
    override fun changes() = invalidations()
}
suspend fun readResult(statement: SQLiteStatement, maxRows: Int, step: suspend () -> Boolean): SqlResult = SqlResult(emptyList(), emptyList())
