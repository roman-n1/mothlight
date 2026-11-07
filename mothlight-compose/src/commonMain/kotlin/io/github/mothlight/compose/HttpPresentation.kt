package io.github.mothlight.compose

import io.github.mothlight.*
import kotlinx.serialization.json.*

internal const val HTTP_SLOW_MILLIS = 1000L

internal fun filterHttp(transactions: List<NetworkTransaction>, query: String, filter: HttpFilter): List<NetworkTransaction> =
    transactions.asReversed().filter { tx ->
        val status = tx.status
        val matches = "${tx.method} ${tx.url} ${statusText(tx.status)}".contains(query, ignoreCase = true)
        matches && when (filter) {
            HttpFilter.All -> true
            HttpFilter.HttpErrors -> status is Status.Completed && status.code >= 400
            HttpFilter.TransportErrors -> tx.status is Status.Failed
            HttpFilter.InProgress -> tx.status == Status.InProgress
            HttpFilter.Slow -> when (status) {
                is Status.Completed -> status.durationMillis >= HTTP_SLOW_MILLIS
                is Status.Failed -> status.durationMillis >= HTTP_SLOW_MILLIS
                Status.InProgress -> false
            }
        }
    }

internal fun httpHost(url: String): String = if (url.contains("://")) url.substringAfter("://").substringBefore('/') else ""
internal fun httpPath(url: String): String = if (url.contains("://")) "/" + url.substringAfter("://").substringAfter('/', "") else url
private val prettyHttpJson = Json { prettyPrint = true }
internal fun formattedHttpBody(value: String): String {
    val trimmed = value.trimStart()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return value
    return runCatching {
        prettyHttpJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(value))
    }.getOrDefault(value)
}
