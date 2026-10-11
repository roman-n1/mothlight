package io.github.mothlight.ktor

import io.github.mothlight.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
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
            assertIs<Status.Completed>(tx.status)
            assertEquals(200, assertIs<Status.Completed>(tx.status).code)
            assertEquals("https://example.test/data", tx.url)
            assertEquals("***", tx.requestHeaders.entries.first { it.key.equals("Authorization", true) }.value)
            assertEquals(CapturedBody.Text("""{"access_token":"***","items":[1,2]}"""), tx.responseBody)
        } finally { client.close() }
    }
    @Test fun customByteArrayUploadIsReadOnlyByTransport() = runTest {
        val inspector = NetworkInspector(redactor = Redactor(captureJsonBodies = true)).apply { setEnabled(true) }
        var reads = 0
        val upload = object : OutgoingContent.ByteArrayContent() {
            override val contentType = ContentType.Application.Json
            override fun bytes(): ByteArray {
                reads++
                check(reads == 1) { "upload replayed" }
                return "{}".encodeToByteArray()
            }
        }
        val client = HttpClient(MockEngine { request ->
            assertEquals("{}", (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            respond("ok")
        }) { installMothlight(inspector) }
        try {
            assertEquals("ok", client.post("https://example.test") { setBody(upload) }.bodyAsText())
            assertEquals(1, reads)
            assertEquals(CapturedBody.NotCaptured, inspector.transactions.value.single().requestBody)
        } finally { client.close() }
    }

    @Test fun oversizedStreamCaptureFinishesBeforeTheStreamEnds() = runTest {
        val inspector = NetworkInspector(redactor = Redactor(captureJsonBodies = true, bodyLimit = 32)).apply { setEnabled(true) }
        val channel = ByteChannel(autoFlush = true)
        val release = CompletableDeferred<Unit>()
        val received = CompletableDeferred<Unit>()
        val prefix = "{\"data\":\"" + "a".repeat(24) // Exactly bodyLimit + 1 bytes, with no following bytes yet.
        val producer = launch {
            channel.writeStringUtf8(prefix)
            release.await()
            channel.writeStringUtf8("\"}")
            channel.close()
        }
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(channel, HttpStatusCode.OK, headersOf("Content-Type", "application/json")) }
        })
        val client = HttpClient(engine) { installMothlight(inspector) }
        val request = async {
            client.prepareGet("https://example.test").execute { response ->
                received.complete(Unit)
                release.await()
                response.bodyAsText()
            }
        }
        try {
            received.await()
            advanceUntilIdle()
            assertEquals(CapturedBody.TooLarge, inspector.transactions.value.single().responseBody)
            release.complete(Unit)
            assertEquals(prefix + "\"}", request.await())
        } finally {
            release.complete(Unit)
            producer.join()
            request.cancelAndJoin()
            client.close()
        }
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
            assertEquals(201, assertIs<Status.Completed>(tx.status).code)
            assertEquals(CapturedBody.NotCaptured, tx.responseBody)
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
            assertEquals("TransportError", assertIs<Status.Failed>(inspector.transactions.value.single().status).error)
        } finally {
            client.close()
            referenceClient.close()
        }
    }
}
