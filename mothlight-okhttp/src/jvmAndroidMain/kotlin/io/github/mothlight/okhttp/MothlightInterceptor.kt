package io.github.mothlight.okhttp

import io.github.mothlight.NetworkInspector
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Application interceptor for OkHttp and Retrofit. No Compose dependency.
 * Never reads or replays request bodies. Optional JSON response capture uses a bounded peek.
 */
class MothlightInterceptor(private val inspector: NetworkInspector) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!inspector.enabled.value) return chain.proceed(request)
        val id = inspector.recordStart(
            method = request.method,
            url = request.url.toString(),
            headers = request.headers.names().associateWith { request.headers.values(it).joinToString(", ") },
            body = null,
            now = System.currentTimeMillis(),
        )
        try {
            val response = chain.proceed(request)
            inspector.recordSuccess(
                id = id,
                code = response.code,
                headers = response.headers.names().associateWith { response.headers.values(it).joinToString(", ") },
                body = capturedBody(response),
                now = System.currentTimeMillis(),
            )
            return response
        } catch (cause: IOException) {
            // Exception messages can contain URLs or credentials; retain only the type.
            inspector.recordFailure(id, cause.javaClass.simpleName, System.currentTimeMillis())
            throw cause
        }
    }

    private fun capturedBody(response: Response): String? {
        if (!inspector.redactor.captureJsonBodies) return null
        val subtype = response.body?.contentType()?.subtype ?: return null
        if (subtype != "json" && !subtype.endsWith("+json")) return null
        return try {
            // Extra byte lets the redactor distinguish an oversized ASCII body from a complete one.
            response.peekBody(inspector.redactor.bodyLimit.toLong() + 1).use { it.string() }
        } catch (_: IOException) {
            // Diagnostics must not replace a response with an observation failure.
            null
        }
    }
}
