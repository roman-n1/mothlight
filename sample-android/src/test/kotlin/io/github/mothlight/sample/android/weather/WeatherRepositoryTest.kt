package io.github.mothlight.sample.android.weather

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WeatherRepositoryTest {
    private val reading = WeatherReading("LISBON", 18.5, 12.0, "2026-10-11T10:00")
    @Test fun successfulRequestSavesBeforeSuccessAndEmitsLogs() = runTest {
        val cache = Cache(); val logs = mutableListOf<String>()
        val repository = WeatherRepository(WeatherRemote { assertEquals(WeatherCity.LISBON, it); reading }, cache, logs::add)
        assertEquals(WeatherUpdate.SAVED, repository.refresh(WeatherCity.LISBON))
        assertEquals(listOf(reading), repository.history().first())
        assertEquals(listOf("refresh:LISBON", "saved:LISBON"), logs)
    }
    @Test fun networkFailurePreservesPreviousCache() = runTest {
        val cache = Cache(); cache.save(reading)
        val repository = WeatherRepository(WeatherRemote { throw java.io.IOException("offline") }, cache) {}
        assertEquals(WeatherUpdate.NETWORK_FAILED, repository.refresh(WeatherCity.TOKYO))
        assertEquals(listOf(reading), repository.history().first())
    }
    @Test fun storageFailureDoesNotClaimDataWasSaved() = runTest {
        val cache = object : WeatherCache {
            override fun history() = flowOf(emptyList<WeatherReading>())
            override suspend fun save(reading: WeatherReading) { error("disk full") }
        }
        assertEquals(WeatherUpdate.STORAGE_FAILED, WeatherRepository(WeatherRemote { reading }, cache) {}.refresh(WeatherCity.LISBON))
    }
    @Test fun cancellationDoesNotBecomeOfflineOrWriteCache() = runTest {
        val cache = Cache()
        assertFailsWith<CancellationException> { WeatherRepository(WeatherRemote { throw CancellationException() }, cache) {}.refresh(WeatherCity.LISBON) }
        assertTrue(cache.history().first().isEmpty())
    }
    private class Cache : WeatherCache {
        private val rows = MutableStateFlow(emptyList<WeatherReading>())
        override fun history() = rows
        override suspend fun save(reading: WeatherReading) { rows.value = listOf(reading) + rows.value }
    }
}
