package io.github.mothlight

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Read-only source. changes emits once when observation is ready, then on invalidation.
 * Collectors own subscription lifetime. Implementations must bound and sanitize returned cells.
 */
interface DatabaseSource {
    suspend fun tables(): List<String>
    suspend fun page(table: String, limit: Int, offset: Int): DatabasePage
    fun changes(): Flow<Unit>
}

data class DatabasePage(val table: String, val columns: List<String>, val rows: List<List<String?>>, val totalRows: Long, val offset: Int, val limit: Int)

/** No background scope: observation stops as soon as the caller cancels. */
class DatabaseBrowser(private val source: DatabaseSource) {
    fun tables(): Flow<List<String>> = source.changes().map { source.tables() }
    fun page(table: String, limit: Int = 50, offset: Int = 0): Flow<DatabasePage> = source.changes().map { source.page(table, limit, offset) }
}
