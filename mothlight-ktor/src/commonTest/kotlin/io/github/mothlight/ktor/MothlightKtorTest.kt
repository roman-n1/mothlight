package io.github.mothlight.ktor

import io.github.mothlight.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class MothlightKtorTest {
    @Test fun responseIsUnchangedAndCapturedSecretsAreMasked() = runTest {
        val inspector = NetworkInspector(redactor = Redactor(captureJsonBodies = true))
        inspector.setEnabled(true)
        val body = """{"access_token":"secret","items":[1,2]}"""
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json")) }) {
            installMothlight(inspector)
        }
        try {
            assertEquals(body, client.get("https://example.test/data?secret=hidden") {
                header("Authorization", "Bearer secret")
            }.bodyAsText())
            advanceUntilIdle()
            val tx = inspector.transactions.value.single()
            assertEquals(Status.Completed, tx.status)
            assertEquals(200, tx.responseCode)
            assertEquals("https://example.test/data", tx.url)
            assertEquals("***", tx.requestHeaders.entries.first { it.key.equals("Authorization", true) }.value)
            assertEquals("""{"access_token":"***","items":[1,2]}""", tx.responseBody)
        } finally { client.close() }
    }
    @Test fun disabledPluginPreservesResponseAndRecordsNothing() = runTest {
        val inspector = NetworkInspector()
        val client = HttpClient(MockEngine { respond("original") }) { installMothlight(inspector) }
        try {
            assertEquals("original", client.get("https://example.test").bodyAsText())
            advanceUntilIdle()
            assertTrue(inspector.transactions.value.isEmpty())
        } finally { client.close() }
    }
    @Test fun defaultCaptureOmitsBodyWhileRecordingStatus() = runTest {
        val inspector = NetworkInspector()
        inspector.setEnabled(true)
        val client = HttpClient(MockEngine { respond("private", HttpStatusCode.Created) }) { installMothlight(inspector) }
        try {
            assertEquals("private", client.get("https://example.test").bodyAsText())
            val tx = inspector.transactions.value.single()
            assertEquals(201, tx.responseCode)
            assertNull(tx.responseBody)
        } finally { client.close() }
    }
    @Test fun transportFailureIsRethrownWithoutPublishingExceptionMessage() = runTest {
        val inspector = NetworkInspector()
        inspector.setEnabled(true)
        val failure = IllegalStateException("private token")
        val client = HttpClient(MockEngine { throw failure }) { installMothlight(inspector) }
        val referenceClient = HttpClient(MockEngine { throw failure })
        try {
            val expected = assertFailsWith<IllegalStateException> { referenceClient.get("https://example.test") }
            val caught = assertFailsWith<IllegalStateException> { client.get("https://example.test") }
            // Coroutine stack recovery may copy exceptions. Compare the observable client contract.
            assertEquals(expected::class, caught::class)
            assertEquals(expected.message, caught.message)
            assertEquals(Status.Failed("IllegalStateException"), inspector.transactions.value.single().status)
        } finally {
            client.close()
            referenceClient.close()
        }
    }
}
