package io.github.mothlight.compose

import io.github.mothlight.*
import kotlin.test.*

class HttpPresentationTest {
    @Test fun separatesHttpErrorsTransportErrorsPendingAndSlowCalls() {
        val input = listOf(
            tx("ok", Status.Completed(200, 10)), tx("redirect", Status.Completed(304, 20)),
            tx("http", Status.Completed(500, 1200)), tx("timeout", Status.Failed("Timeout", 1500)),
            tx("pending", Status.InProgress),
        )
        assertEquals(listOf("http"), filterHttp(input, "", HttpFilter.HttpErrors).map { it.id })
        assertEquals(listOf("timeout"), filterHttp(input, "", HttpFilter.TransportErrors).map { it.id })
        assertEquals(listOf("pending"), filterHttp(input, "", HttpFilter.InProgress).map { it.id })
        assertEquals(listOf("timeout", "http"), filterHttp(input, "", HttpFilter.Slow).map { it.id })
        assertEquals(listOf("http"), filterHttp(input, "500", HttpFilter.All).map { it.id })
        assertEquals(listOf("timeout"), filterHttp(input, "TIMEOUT", HttpFilter.All).map { it.id })
        assertEquals(listOf("ok", "redirect", "http", "timeout", "pending"), input.map { it.id })
    }
    @Test fun formatsCapturedJsonWithoutInventingOmittedBodies() {
        assertTrue(formattedHttpBody("""{"a":[1,true]}""").contains("\n"))
        assertEquals("unstructured", formattedHttpBody("unstructured"))
        assertEquals("/v2/items", httpPath("https://example.com/v2/items"))
        assertEquals("example.com", httpHost("https://example.com/v2/items"))
        assertEquals("/", httpPath("https://example.com"))
        assertEquals("/relative", httpPath("/relative"))
    }
    private fun tx(id: String, status: Status) = NetworkTransaction(id, 1, "GET", "https://example.com/items",
        emptyMap(), CapturedBody.NotCaptured, emptyMap(), CapturedBody.NotCaptured, 0, status)
}
