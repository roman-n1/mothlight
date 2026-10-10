package io.github.mothlight.sample

import io.github.mothlight.*
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.mothlight.room3.RoomDatabaseSource
import io.github.mothlight.sqlite.TracingSQLiteDriver
import kotlinx.coroutines.*
import io.github.mothlight.retention.RetentionWatcher
import io.github.mothlight.ktor.installMothlight
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.getTimeMillis

/** Synthetic data only. The mock engine makes no network connections. */
class DemoSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val retentionWatcher = RetentionWatcher(scope)
    private var heldObject: Any? = null
    fun retainObject(label: String, reason: String) {
        val target = ByteArray(1024)
        heldObject = target
        retentionWatcher.expectReleased(target, label, reason)
    }
    fun releaseObject(label: String) {
        if (heldObject != null) event("retention", label)
        heldObject = null
    }
    val mothlight = Mothlight(redactor = Redactor(captureJsonBodies = true))
    private val database = Room.inMemoryDatabaseBuilder<DemoDatabase>()
        .setDriver(TracingSQLiteDriver(BundledSQLiteDriver(), mothlight.sql, { mothlight.enabled.value }))
        .setQueryCoroutineContext(Dispatchers.IO).build()
    val databaseSource = RoomDatabaseSource(database)
    private var sequence = 0L
    private val client = HttpClient(MockEngine {
        respond("""{"message":"Hello from Mothlight","access_token":"demo-secret","items":[1,2,3]}""",
            HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
    }) { installMothlight(mothlight.network) }
    init {
        mothlight.setEnabled(true)
        retentionWatcher.setEnabled(true)
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
    suspend fun addRowAndFrame() {
        val id = ++sequence
        database.items().insert(DemoItem(id, "Demo item #$id", "demo-secret"))
        mothlight.realtime.connection("demo", ConnectionState.CONNECTED, getTimeMillis())
        mothlight.realtime.record("demo", FrameDirection.INCOMING, """{"item":$id,"token":"demo-secret"}""", getTimeMillis())
    }
    fun close() { retentionWatcher.close(); heldObject = null; scope.cancel(); client.close(); database.close() }
}
