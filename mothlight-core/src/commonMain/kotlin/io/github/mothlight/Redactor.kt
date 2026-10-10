package io.github.mothlight

import kotlinx.serialization.json.*

/** Runs before data enters an observable feed. Bodies are omitted by default. */
class Redactor(
    val captureJsonBodies: Boolean = false,
    val bodyLimit: Int = 8192,
    private val additionalSensitiveKeys: Set<String> = emptySet(),
) {
    init { require(bodyLimit > 0) }
    fun headers(headers: Map<String, String>): Map<String, String> = headers.mapValues { (key, value) ->
        if (sensitive(key)) "***" else value.take(bodyLimit)
    }
    // Strip credentials, all query values and the fragment; endpoint paths remain visible.
    fun url(value: String): String {
        val base = value.substringBefore('?').substringBefore('#')
        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return base.take(bodyLimit)
        val prefix = base.substring(0, schemeEnd + 3)
        val remainder = base.substring(schemeEnd + 3)
        val authority = remainder.substringBefore('/')
        val path = remainder.removePrefix(authority)
        return (prefix + authority.substringAfterLast('@') + path).take(bodyLimit)
    }
    fun body(value: String?): String? {
        if (!captureJsonBodies || value == null) return null
        // Do not parse or retain oversized/non-JSON content. Truncating before masking can expose secrets.
        if (value.length > bodyLimit) return "[body omitted: size limit]"
        val element = runCatching { Json.parseToJsonElement(value) }.getOrNull()
            ?: return "[body omitted: not JSON]"
        if (element !is JsonObject && element !is JsonArray) return "[body omitted: not JSON]"
        return scrub(element).toString()
    }
    private fun scrub(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (sensitive(key)) JsonPrimitive("***") else scrub(value)
        })
        is JsonArray -> JsonArray(element.map(::scrub))
        else -> element
    }
    private fun sensitive(key: String): Boolean {
        val normalized = key.lowercase().filter(Char::isLetterOrDigit)
        return listOf("token", "password", "secret", "auth", "login", "username", "cookie", "apikey", "credential")
            .any(normalized::contains) || additionalSensitiveKeys.any { it.equals(key, ignoreCase = true) }
    }
}
