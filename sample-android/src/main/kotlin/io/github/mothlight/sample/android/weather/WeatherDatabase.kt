package io.github.mothlight.sample.android.weather

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "weather_readings")
data class WeatherRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val city: String,
    val temperature: Double,
    val wind: Double,
    val observedAt: String,
)

@Dao
abstract class WeatherDao {
    @Query("SELECT * FROM weather_readings ORDER BY id DESC LIMIT 20")
    abstract fun history(): Flow<List<WeatherRow>>
    @Insert abstract suspend fun insert(row: WeatherRow)
    @Query("DELETE FROM weather_readings WHERE id NOT IN (SELECT id FROM weather_readings ORDER BY id DESC LIMIT 200)")
    abstract suspend fun trim()
    @Transaction open suspend fun save(row: WeatherRow) { insert(row); trim() }
}

/** Separate persistent demo database. The old in-memory diagnostics database is unchanged. */
@Database(entities = [WeatherRow::class], version = 1, exportSchema = true)
abstract class WeatherDatabase : RoomDatabase() { abstract fun weather(): WeatherDao }

class RoomWeatherCache(private val dao: WeatherDao) : WeatherCache {
    override fun history() = dao.history().map { rows -> rows.map { WeatherReading(it.city, it.temperature, it.wind, it.observedAt) } }
    override suspend fun save(reading: WeatherReading) {
        dao.save(WeatherRow(city = reading.city, temperature = reading.temperature, wind = reading.wind, observedAt = reading.observedAt))
    }
}
