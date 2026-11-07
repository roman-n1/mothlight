package io.github.mothlight.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import kotlinx.serialization.json.*

internal enum class JsonBodyMode { Tree, Text }
internal class JsonBodyState {
    var mode by mutableStateOf(JsonBodyMode.Tree)
    var expandedPaths by mutableStateOf(setOf(""))
    val list = LazyListState()
    fun toggle(path: String) { expandedPaths = if (path in expandedPaths) expandedPaths - path else expandedPaths + path }
}

internal sealed interface HttpJson {
    data object Plain : HttpJson
    data class Structured(val root: JsonElement) : HttpJson
}

internal fun parseHttpJson(value: String): HttpJson {
    val text = value.trimStart()
    if (!text.startsWith("{") && !text.startsWith("[")) return HttpJson.Plain
    return runCatching<HttpJson> { HttpJson.Structured(Json.parseToJsonElement(value)) }.getOrDefault(HttpJson.Plain)
}

internal enum class JsonNodeKind { Object, Array, String, Number, Boolean, Null }
internal data class JsonTreeRow(val path: String, val name: String, val preview: String, val depth: Int,
    val expandable: Boolean, val expanded: Boolean, val kind: JsonNodeKind)
internal data class JsonTreeRows(val rows: List<JsonTreeRow>, val truncated: Boolean)
private data class PendingJsonNode(val path: String, val name: String, val value: JsonElement, val depth: Int)

/** JSON Pointer paths distinguish object keys, array indices and escaped slash/tilde keys. */
internal fun jsonPointerChild(parent: String, key: String) = parent + "/" + key.replace("~", "~0").replace("/", "~1")

/** Iterative traversal and a row budget keep expanded large/deep payloads bounded. */
internal fun jsonTreeRows(root: JsonElement, expandedPaths: Set<String>, maxRows: Int = 2000): JsonTreeRows {
    require(maxRows > 0)
    val pending = ArrayDeque<PendingJsonNode>()
    pending.addLast(PendingJsonNode("", "", root, 0))
    val rows = mutableListOf<JsonTreeRow>()
    while (pending.isNotEmpty() && rows.size < maxRows) {
        val node = pending.removeLast()
        val value = node.value
        val kind = when (value) {
            is JsonObject -> JsonNodeKind.Object
            is JsonArray -> JsonNodeKind.Array
            JsonNull -> JsonNodeKind.Null
            is JsonPrimitive -> when {
                value.isString -> JsonNodeKind.String
                value.content == "true" || value.content == "false" -> JsonNodeKind.Boolean
                else -> JsonNodeKind.Number
            }
        }
        val expandable = when (value) {
            is JsonObject -> value.isNotEmpty()
            is JsonArray -> value.isNotEmpty()
            else -> false
        }
        val expanded = expandable && node.path in expandedPaths
        val preview = when (value) {
            is JsonObject -> if (value.isEmpty()) "{}" else "{${value.size}}"
            is JsonArray -> if (value.isEmpty()) "[]" else "[${value.size}]"
            else -> value.toString()
        }
        rows += JsonTreeRow(node.path, node.name, preview, node.depth, expandable, expanded, kind)
        if (expanded) when (value) {
            is JsonObject -> value.entries.toList().asReversed().forEach { (key, child) ->
                pending.addLast(PendingJsonNode(jsonPointerChild(node.path, key), JsonPrimitive(key).toString(), child, node.depth + 1))
            }
            is JsonArray -> value.indices.reversed().forEach { index ->
                pending.addLast(PendingJsonNode(jsonPointerChild(node.path, index.toString()), "[$index]", value[index], node.depth + 1))
            }
            else -> Unit
        }
    }
    return JsonTreeRows(rows, pending.isNotEmpty())
}
