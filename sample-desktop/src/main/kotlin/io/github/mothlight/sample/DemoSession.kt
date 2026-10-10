package io.github.mothlight.sample

import io.github.mothlight.*
import io.github.mothlight.ktor.installMothlight
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.getTimeMillis

/** Synthetic data only. The mock engine makes no network connections. */
class DemoSession {
    val mothlight = Mothlight(redactor = Redactor(captureJsonBodies = true))
    private var sequence = 0L
    private val client = HttpClient(MockEngine {
        respond("""{"message":"Hello from Mothlight","access_token":"demo-secret","items":[1,2,3]}""",
            HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
    }) { installMothlight(mothlight.network) }
    init {
        mothlight.setEnabled(true)
        event("startup", "Demo ready")
        log()
    }
    suspend fun request() {
        client.get("https://demo.mothlight.test/items?token=demo-secret") { header("Authorization", "Bearer demo-secret") }.bodyAsText()
        event("http", "Mock request completed")
    }
    fun log() {
        sequence++
        mothlight.logs.record(LogEntry("log-$sequence", getTimeMillis(), LogLevel.INFO, "Demo", "Synthetic log entry #$sequence"))
    }
    fun event(category: String = "custom", message: String = "Host plugin action") {
        sequence++
        mothlight.events.record(DiagnosticEvent("event-$sequence", getTimeMillis(), category, message))
    }
    fun close() { client.close() }
}
