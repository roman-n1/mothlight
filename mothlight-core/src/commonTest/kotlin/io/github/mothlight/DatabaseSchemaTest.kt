package io.github.mothlight

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseSchemaTest {
    @Test fun optionalMetadataDoesNotBreakExistingSources() = runTest {
        val source = object : DatabaseSource {
            override suspend fun tables() = listOf("items")
            override suspend fun page(table: String, limit: Int, offset: Int) = DatabasePage(table, emptyList(), emptyList(), 0, offset, limit)
            override fun changes() = flowOf(Unit)
        }
        assertEquals(DatabaseSchema.Unsupported, DatabaseBrowser(source).schema("items").first())
    }

    @Test fun metadataRefreshesAndReleasesObservation() = runTest {
        val invalidations = MutableSharedFlow<Unit>()
        var observers = 0
        var reads = 0
        val source = object : DatabaseSource {
            override suspend fun tables() = listOf("items")
            override suspend fun page(table: String, limit: Int, offset: Int) = error("Schema must not read rows")
            override suspend fun schema(table: String): DatabaseSchema {
                reads++
                return DatabaseSchema.Available(List(reads) { DatabaseColumn("c$it", "TEXT", false, 0) })
            }
            override fun changes() = flow {
                observers++
                try { emit(Unit); emitAll(invalidations) } finally { observers-- }
            }
        }
        val schemas = mutableListOf<DatabaseSchema>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { DatabaseBrowser(source).schema("items").toList(schemas) }
        assertEquals(1, observers)
        assertEquals(1, schemas.size)
        invalidations.emit(Unit)
        assertEquals(2, assertIs<DatabaseSchema.Available>(schemas.last()).columns.size)
        job.cancelAndJoin()
        assertEquals(0, observers)
        invalidations.emit(Unit)
        assertEquals(2, reads)
    }
}
