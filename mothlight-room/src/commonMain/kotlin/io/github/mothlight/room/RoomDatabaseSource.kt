package io.github.mothlight.room

import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import io.github.mothlight.*
import io.github.mothlight.sqlite.*
import kotlinx.coroutines.flow.*

/** Attach the app-owned database; this adapter never closes it or creates a global registry. */
class RoomDatabaseSource(private val database: RoomDatabase, redactor: Redactor = Redactor()) : DatabaseSource {
    private val reader = SQLiteDatabaseSource(SqlReadExecutor { sql, maxRows ->
        database.useReaderConnection { connection ->
            connection.usePrepared(sql) { statement -> readResult(statement, maxRows) { statement.step() } }
        }
    }, { changes() }, redactor)
    override suspend fun tables() = reader.tables()
    override suspend fun page(table: String, limit: Int, offset: Int) = reader.page(table, limit, offset)
    override fun changes(): Flow<Unit> = flow {
        val names = tables()
        if (names.isEmpty()) { emit(Unit); return@flow }
        emitAll(database.invalidationTracker.createFlow(*names.toTypedArray(), emitInitialState = true).map { Unit })
    }
}
