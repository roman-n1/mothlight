package io.github.mothlight.sample.android.weather

import io.github.mothlight.*
import io.github.mothlight.okhttp.MothlightInterceptor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.*

class OpenMeteoRemoteTest {
    @Test fun retrofitResponseIsUsableAndVisibleInInspector() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"current":{"temperature_2m":18.5,"wind_speed_10m":12.0,"time":"2026-10-11T10:00"}}"""))
            val mothlight = Mothlight(redactor = Redactor(captureJsonBodies = true)).apply { setEnabled(true) }
            val client = OkHttpClient.Builder().addInterceptor(MothlightInterceptor(mothlight.network)).build()
            try {
                val reading = weatherRemote(client, server.url("/").toString()).load(WeatherCity.LISBON)
                assertEquals(18.5, reading.temperature)
                assertEquals(12.0, reading.wind)
                val request = assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                assertEquals("/v1/forecast", request.requestUrl!!.encodedPath)
                assertEquals("38.72", request.requestUrl!!.queryParameter("latitude"))
                assertEquals("temperature_2m,wind_speed_10m", request.requestUrl!!.queryParameter("current"))
                val transaction = mothlight.network.transactions.value.single()
                assertEquals(200, assertIs<Status.Completed>(transaction.status).code)
                assertTrue(assertIs<CapturedBody.Text>(transaction.responseBody).value.contains("18.5"))
                assertFalse(transaction.url.contains("latitude"))
            } finally {
                client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
            }
        }
    }
    @Test fun malformedOrHttpErrorResponsesNeverProduceAReading() = runTest {
        MockWebServer().use { server ->
            val client = OkHttpClient()
            try {
                val remote = weatherRemote(client, server.url("/").toString())
                server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
                assertFailsWith<retrofit2.HttpException> { remote.load(WeatherCity.TOKYO) }
                server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"current":{"temperature_2m":12,"wind_speed_10m":-1,"time":"now"}}"""))
                assertFailsWith<java.io.IOException> { remote.load(WeatherCity.TOKYO) }
            } finally {
                client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
            }
        }
    }
}
