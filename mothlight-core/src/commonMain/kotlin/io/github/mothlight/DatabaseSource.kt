package io.github.mothlight

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

/** Read-only source. changes emits once when observation is ready, then on invalidation.
 * Collectors own subscription lifetime. Implementations must bound and sanitize returned cells.
 */
interface DatabaseSource {
    suspend fun tables(): List<String>
    suspend fun page(table: String, limit: Int, offset: Int): DatabasePage
    fun changes(): Flow<Unit>
}

data class DatabasePage(val table: String, val columns: List<String>, val rows: List<List<DatabaseCell>>, val totalRows: Long, val offset: Int, val limit: Int)

/** No background scope: observation stops as soon as the caller cancels. */
class DatabaseBrowser(private val source: DatabaseSource) {
    // Setup belongs to collection too, so UI catch operators handle setup and query failures alike.
    private fun changes(): Flow<Unit> = flow { emitAll(source.changes()) }
    fun tables(): Flow<List<String>> = changes().map { source.tables() }
    fun page(table: String, limit: Int = 50, offset: Int = 0): Flow<DatabasePage> = changes().map { source.page(table, limit, offset) }
}
