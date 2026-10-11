package io.github.mothlight.ktor

import io.github.mothlight.NetworkInspector
import io.github.mothlight.CapturedBody
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.content.TextContent
import io.ktor.http.charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.String
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
import io.ktor.util.AttributeKey
import io.ktor.util.date.getTimeMillis

/**
 * Passive Ktor observer adapted from the original Debug panel.
 * Streaming request bodies are never read. Response bodies are opt-in JSON only.
 * Use only in development clients. Own one inspector per application/session.
 */
fun HttpClientConfig<*>.installMothlight(inspector: NetworkInspector) {
    install(createClientPlugin("Mothlight") {
        on(Send) { request ->
            if (!inspector.enabled.value) return@on proceed(request)
            val id = inspector.recordStart(
                method = request.method.value,
                url = request.url.buildString(),
                headers = request.headers.entries().associate { (name, values) -> name to values.joinToString(", ") },
                body = if (inspector.redactor.captureJsonBodies) extractBody(request) else CapturedBody.NotCaptured,
                now = getTimeMillis(),
            )
            request.attributes.put(TransactionIdKey, id)
            try {
                val call = proceed(request)
                inspector.recordSuccess(id, call.response.status.value,
                    call.response.headers.entries().associate { (name, values) -> name to values.joinToString(", ") },
                    CapturedBody.NotCaptured, getTimeMillis())
                call
            } catch (cause: Throwable) {
                // Stable category: no credential-bearing message or reflective class name.
                inspector.recordFailure(id, "TransportError", getTimeMillis())
                throw cause
            }
        }
    })
    if (inspector.redactor.captureJsonBodies) {
        // The observer receives a separate body. Caller still receives the original response.
        install(ResponseObserver) {
            filter { call ->
                val type = call.response.headers["Content-Type"].orEmpty().substringBefore(';').trim()
                inspector.enabled.value && (type == "application/json" || type.endsWith("+json")) &&
                    call.request.attributes.contains(TransactionIdKey)
            }
            onResponse { response ->
                val id = response.call.request.attributes.getOrNull(TransactionIdKey) ?: return@onResponse
                val body = try {
                    val bytes = response.bodyAsChannel().readRemaining(inspector.redactor.bodyLimit.toLong() + 1)
                        .use { it.readByteArray() }
                    if (bytes.size > inspector.redactor.bodyLimit) CapturedBody.TooLarge
                    else CapturedBody.Text(String(bytes, 0, bytes.size, response.charset() ?: Charsets.UTF_8))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    CapturedBody.NotCaptured
                }
                inspector.recordSuccess(id, response.status.value,
                    response.headers.entries().associate { (name, values) -> name to values.joinToString(", ") },
                    body, getTimeMillis())
            }
        }
    }
}
private val TransactionIdKey = AttributeKey<String>("Mothlight.transactionId")
private fun extractBody(request: HttpRequestBuilder): CapturedBody = when (val body = request.body) {
    // TextContent is already materialized; arbitrary bytes() implementations may be one-shot.
    is TextContent -> CapturedBody.Text(body.text)
    else -> CapturedBody.NotCaptured
}
