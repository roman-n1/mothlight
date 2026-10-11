package io.github.mothlight.sample.android.weather

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mothlight.*
import io.github.mothlight.room.RoomDatabaseSource
import io.github.mothlight.sqlite.TracingSQLiteDriver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherDatabaseTest {
    @Test fun fileDatabaseSurvivesReopenAndInspectorReceivesInvalidation() = runBlocking {
        withTimeout(30000) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "weather-test-${UUID.randomUUID()}.db"
            val mothlight = Mothlight().apply { setEnabled(true) }
            fun open() = Room.databaseBuilder(context, WeatherDatabase::class.java, name)
                .setDriver(TracingSQLiteDriver(BundledSQLiteDriver(), mothlight.sql, { mothlight.enabled.value }))
                .setQueryCoroutineContext(Dispatchers.IO).build()
            try {
                val db = open()
                try {
                    val pages = Channel<DatabasePage>(Channel.UNLIMITED)
                    val observer = launch { DatabaseBrowser(RoomDatabaseSource(db)).page("weather_readings").collect { pages.send(it) } }
                    try {
                        assertEquals(0L, pages.receive().totalRows)
                        RoomWeatherCache(db.weather()).save(WeatherReading("LISBON", 18.5, 12.0, "2026-10-11T10:00"))
                        val updated = pages.receive()
                        assertEquals(1L, updated.totalRows)
                        assertTrue(updated.rows.single().contains(DatabaseCell.Text("LISBON")))
                    } finally { observer.cancelAndJoin() }
                } finally { db.close() }
                val reopened = open()
                try {
                    assertEquals(18.5, RoomWeatherCache(reopened.weather()).history().first().single().temperature, 0.0)
                    repeat(205) { RoomWeatherCache(reopened.weather()).save(WeatherReading("TOKYO", it.toDouble(), 1.0, "test-$it")) }
                    val page = RoomDatabaseSource(reopened).page("weather_readings", 1, 0)
                    assertEquals(200L, page.totalRows)
                    val history = RoomWeatherCache(reopened.weather()).history().first()
                    assertEquals(20, history.size)
                    assertEquals(204.0, history.first().temperature, 0.0)
                    assertTrue(mothlight.sql.snapshot.value.events.isNotEmpty())
                } finally { reopened.close() }
            } finally { context.deleteDatabase(name) }
        }
    }
}
