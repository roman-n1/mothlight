package io.github.mothlight.sample.android.weather

import kotlinx.coroutines.flow.Flow

data class WeatherReading(val city: String, val temperature: Double, val wind: Double, val observedAt: String)
enum class WeatherCity(val latitude: Double, val longitude: Double) { NOVOSIBIRSK(55.03, 82.92), LISBON(38.72, -9.14), TOKYO(35.68, 139.69) }
fun interface WeatherRemote { suspend fun load(city: WeatherCity): WeatherReading }
interface WeatherCache { fun history(): Flow<List<WeatherReading>>; suspend fun save(reading: WeatherReading) }
enum class WeatherUpdate { SAVED, NETWORK_FAILED, STORAGE_FAILED }
class WeatherRepository(private val remote: WeatherRemote, private val cache: WeatherCache, private val log: (String) -> Unit) {
    fun history() = cache.history()
    suspend fun refresh(city: WeatherCity): WeatherUpdate {
        log("refresh:${city.name}")
        val reading = try { remote.load(city) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { log("network_failed:${city.name}"); return WeatherUpdate.NETWORK_FAILED }
        try { cache.save(reading) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { log("storage_failed:${city.name}"); return WeatherUpdate.STORAGE_FAILED }
        log("saved:${city.name}")
        return WeatherUpdate.SAVED
    }
}
