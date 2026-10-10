package io.github.mothlight

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest

class NetworkInspectorTest {
    @Test fun disabledInspectorDoesNotRecord() {
        val inspector = NetworkInspector()
        inspector.recordStart("GET", "https://example.test", emptyMap(), CapturedBody.NotCaptured, 0)
        assertEquals(emptyList(), inspector.transactions.value)
    }
    @Test fun redactionHappensBeforePublicationAndNestedJsonIsMasked() {
        val inspector = NetworkInspector(redactor = Redactor(captureJsonBodies = true))
        inspector.setEnabled(true)
        val id = inspector.recordStart("POST", "https://user:secret@example.test/items?token=hidden#hidden", mapOf("aUtHoRiZaTiOn" to "secret", "X-Api-Key" to "secret", "Accept" to "application/json"), """{"nested":[{"access_token":"secret","name":"visible"}],"password":42}""", 10)
        inspector.recordSuccess(id, 201, mapOf("Set-Cookie" to "secret"), """{"ok":true}""", 35)
        val tx = inspector.transactions.value.single()
        assertEquals("https://example.test/items", tx.url)
        assertEquals(mapOf("aUtHoRiZaTiOn" to "***", "X-Api-Key" to "***", "Accept" to "application/json"), tx.requestHeaders)
        assertEquals(CapturedBody.Text("""{"nested":[{"access_token":"***","name":"visible"}],"password":"***"}"""), tx.requestBody)
        assertEquals(mapOf("Set-Cookie" to "***"), tx.responseHeaders)
        assertEquals(Status.Completed(201, 25), tx.status)
    }
    @Test fun bodiesAreOptInAndMalformedOrOversizedBodiesAreNotRetained() {
        assertEquals(CapturedBody.NotCaptured, Redactor().body("""{"message":"private"}"""))
        val redactor = Redactor(captureJsonBodies = true, bodyLimit = 40)
        assertEquals(CapturedBody.NotJson, redactor.body("password=private"))
        assertEquals(CapturedBody.TooLarge, redactor.body("s".repeat(41)))
        assertEquals(CapturedBody.Text("""{"x":"***"}"""), Redactor(true, additionalSensitiveKeys = setOf("x")).body("""{"x":"private"}"""))
    }
    @Test fun lateResponseDoesNotResurrectEvictedOrClearedRequest() {
        val inspector = NetworkInspector(capacity = 1)
        inspector.setEnabled(true)
        val first = inspector.recordStart("GET", "/first", emptyMap(), CapturedBody.NotCaptured, 0)
        val second = inspector.recordStart("GET", "/second", emptyMap(), CapturedBody.NotCaptured, 1)
        inspector.recordSuccess(first, 200, emptyMap(), CapturedBody.NotCaptured, 2)
        assertEquals(listOf(second), inspector.transactions.value.map { it.id })
        assertEquals(Status.InProgress, inspector.transactions.value.single().status)
        inspector.clear()
        inspector.recordSuccess(second, 200, emptyMap(), CapturedBody.NotCaptured, 3)
        assertEquals(emptyList(), inspector.transactions.value)
        val third = inspector.recordStart("GET", "/third", emptyMap(), CapturedBody.NotCaptured, 4)
        assertNotEquals(first, third)
        assertNotEquals(second, third)
    }
    @Test fun twoSessionsHaveIndependentGatesAndBuffers() {
        val first = Mothlight()
        val second = Mothlight()
        first.setEnabled(true)
        first.network.recordStart("GET", "/a", emptyMap(), CapturedBody.NotCaptured, 0)
        second.network.recordStart("GET", "/b", emptyMap(), CapturedBody.NotCaptured, 0)
        first.logs.record(LogEntry("1", 0, LogLevel.INFO, "test", "message"))
        first.events.record(DiagnosticEvent("1", 0, "startup", "ready"))
        assertEquals(1, first.network.transactions.value.size)
        assertEquals(1, first.logs.items.value.size)
        assertEquals(1, first.events.items.value.size)
        assertTrue(second.network.transactions.value.isEmpty())
        first.setEnabled(false)
        first.logs.record(LogEntry("2", 0, LogLevel.INFO, "test", "discarded"))
        assertEquals(1, first.logs.items.value.size)
        first.clear()
        assertTrue(first.logs.items.value.isEmpty())
        assertTrue(first.events.items.value.isEmpty())
        assertTrue(first.network.transactions.value.isEmpty())
    }
    @Test fun concurrentRequestsKeepUniqueIdsAndAllCompletions() = runTest {
        val inspector = NetworkInspector(capacity = 100)
        inspector.setEnabled(true)
        coroutineScope {
            repeat(100) { index -> launch(Dispatchers.Default) {
                val id = inspector.recordStart("GET", "/$index", emptyMap(), CapturedBody.NotCaptured, 10)
                inspector.recordSuccess(id, 200, emptyMap(), CapturedBody.NotCaptured, 30)
            } }
        }
        assertEquals(100, inspector.transactions.value.map { it.id }.toSet().size)
        assertTrue(inspector.transactions.value.all { it.status is Status.Completed })
    }
}
