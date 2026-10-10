package io.github.mothlight.room

import androidx.room.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.mothlight.DatabaseBrowser
import io.github.mothlight.DatabaseCell
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import kotlin.test.*

@Entity(tableName = "items")
data class Item(@PrimaryKey val id: Int, val name: String, val token: String)
@Dao interface ItemDao {
    @Insert suspend fun insert(item: Item)
    @Query("UPDATE items SET name = :name WHERE id = 1") suspend fun rename(name: String)
}
@Database(entities = [Item::class], version = 1, exportSchema = false)
abstract class TestDatabase : RoomDatabase() { abstract fun items(): ItemDao }

class RoomDatabaseSourceTest {
    @Test fun actualRoomInvalidationRefreshesVisibleRows() = runTest {
        val db = Room.inMemoryDatabaseBuilder<TestDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler)).build()
        try {
            db.items().insert(Item(1, "before", "private"))
            val source = RoomDatabaseSource(db)
            assertEquals(listOf("items"), source.tables())
            val snapshots = Channel<io.github.mothlight.DatabasePage>(Channel.UNLIMITED)
            val job = backgroundScope.launch { DatabaseBrowser(source).page("items").collect { snapshots.send(it) } }
            assertEquals(listOf(DatabaseCell.Text("1"), DatabaseCell.Text("before"), DatabaseCell.Text("***")), snapshots.receive().rows.single())
            db.items().rename("after")
            // An observable invalidation signal, not a delay or real-time polling.
            assertEquals(listOf(DatabaseCell.Text("1"), DatabaseCell.Text("after"), DatabaseCell.Text("***")), snapshots.receive().rows.single())
            job.cancelAndJoin()
        } finally { db.close() }
    }
}
