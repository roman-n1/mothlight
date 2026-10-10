package io.github.mothlight.sample.android

import androidx.room.*

@Entity(tableName = "demo_items")
data class DemoItem(@PrimaryKey val id: Long, val name: String, val token: String)
@Dao interface DemoItems {
    @Insert suspend fun insert(item: DemoItem)
}
@Database(entities = [DemoItem::class], version = 1, exportSchema = false)
abstract class DemoDatabase : RoomDatabase() { abstract fun items(): DemoItems }
