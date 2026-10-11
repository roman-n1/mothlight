package io.github.mothlight.sample.android.weather

import java.io.IOException
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun current(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") fields: String = "temperature_2m,wind_speed_10m",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") days: Int = 1,
    ): ResponseBody
}

/** Public Open-Meteo demo; fixed cities, no location permission, account or API key. */
class OpenMeteoRemote(private val service: OpenMeteoService) : WeatherRemote {
    override suspend fun load(city: WeatherCity): WeatherReading {
        val payload = service.current(city.latitude, city.longitude).use { it.string() }
        val current = Json.parseToJsonElement(payload).jsonObject.getValue("current").jsonObject
        val temperature = current.getValue("temperature_2m").jsonPrimitive.double
        val wind = current.getValue("wind_speed_10m").jsonPrimitive.double
        val time = current.getValue("time").jsonPrimitive.content
        if (!temperature.isFinite() || !wind.isFinite() || wind < 0 || time.isBlank()) throw IOException("Invalid weather measurements")
        return WeatherReading(city.name, temperature, wind, time)
    }
}

fun weatherRemote(client: OkHttpClient, baseUrl: String = "https://api.open-meteo.com/"): WeatherRemote =
    OpenMeteoRemote(Retrofit.Builder().baseUrl(baseUrl).client(client).build().create(OpenMeteoService::class.java))
