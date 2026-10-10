package io.github.mothlight.okhttp

import io.github.mothlight.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSink
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import java.io.IOException
import kotlin.test.*

class MothlightInterceptorTest {
    interface Api { @GET("items?token=secret") fun items(): Call<ResponseBody> }
    private fun client(inspector: NetworkInspector, terminal: Interceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(MothlightInterceptor(inspector)).addInterceptor(terminal).build()
    private fun response(chain: Interceptor.Chain, body: String = """{"token":"secret","ok":true}"""): Response =
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .header("Set-Cookie", "session=secret").body(body.toResponseBody("application/json".toMediaType())).build()
    @Test fun retrofitReceivesOriginalResponseWhilePanelGetsRedactedCopy() {
        val inspector = NetworkInspector(redactor = Redactor(captureJsonBodies = true))
        inspector.setEnabled(true)
        val client = client(inspector) { response(it) }
        val api = Retrofit.Builder().baseUrl("https://example.test/").client(client).build().create(Api::class.java)
        val result = api.items().execute()
        result.body()!!.use { assertEquals("""{"token":"secret","ok":true}""", it.string()) }
        val tx = inspector.transactions.value.single()
        assertEquals(Status.Completed, tx.status)
        assertEquals("https://example.test/items", tx.url)
        assertEquals("***", tx.responseHeaders["set-cookie"] ?: tx.responseHeaders["Set-Cookie"])
        assertEquals("""{"token":"***","ok":true}""", tx.responseBody)
    }
    @Test fun disabledInterceptorStillReturnsResponseWithoutRecording() {
        val inspector = NetworkInspector()
        client(inspector) { response(it) }.newCall(Request.Builder().url("https://example.test").build()).execute().use {
            assertEquals(200, it.code)
            assertNotNull(it.body!!.string())
        }
        assertTrue(inspector.transactions.value.isEmpty())
    }
    @Test fun transportFailureIsRethrownAndMessageIsNotRetained() {
        val inspector = NetworkInspector()
        inspector.setEnabled(true)
        val failure = IOException("private secret")
        val client = client(inspector) { throw failure }
        val caught = assertFailsWith<IOException> {
            client.newCall(Request.Builder().url("https://example.test").build()).execute()
        }
        assertSame(failure, caught)
        assertEquals(Status.Failed("IOException"), inspector.transactions.value.single().status)
    }
    @Test fun oneShotUploadIsNotReadByInspector() {
        val inspector = NetworkInspector(redactor = Redactor(captureJsonBodies = true))
        inspector.setEnabled(true)
        var writes = 0
        val body = object : RequestBody() {
            override fun contentType(): MediaType = "application/json".toMediaType()
            override fun isOneShot(): Boolean = true
            override fun writeTo(sink: BufferedSink) { writes++; sink.writeUtf8("upload") }
        }
        val client = client(inspector) {
            val sent = Buffer()
            it.request().body!!.writeTo(sent)
            assertEquals("upload", sent.readUtf8())
            response(it)
        }
        client.newCall(Request.Builder().url("https://example.test").post(body).build()).execute().close()
        assertEquals(1, writes)
        assertNull(inspector.transactions.value.single().requestBody)
    }
    @Test fun largeBodyIsOmittedAndOriginalRemainsReadable() {
        val inspector = NetworkInspector(redactor = Redactor(captureJsonBodies = true, bodyLimit = 32))
        inspector.setEnabled(true)
        val original = """{"data":"${"a".repeat(100)}"}"""
        client(inspector) { response(it, original) }.newCall(Request.Builder().url("https://example.test").build()).execute().use {
            assertEquals(original, it.body!!.string())
        }
        assertEquals("[body omitted: size limit]", inspector.transactions.value.single().responseBody)
    }
}
