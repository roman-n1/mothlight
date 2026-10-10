package io.github.mothlight.compose

import io.github.mothlight.*

internal data class NetworkRow(val id: String, val title: String, val subtitle: String, val duration: String)
internal data class FeedRow(val id: String, val message: String, val label: String)

internal fun networkRows(transactions: List<NetworkTransaction>, query: String): List<NetworkRow> =
    transactions.asReversed().filter { "${it.method} ${it.url}".contains(query, ignoreCase = true) }.map {
        NetworkRow(it.id, "${it.method} ${it.url}", "#${it.sequenceNumber} · ${statusText(it.status)}", durationText(it.status))
    }
internal fun selectedTransaction(transactions: List<NetworkTransaction>, selection: NetworkSelection): RequestDetails = when (selection) {
    NetworkSelection.Unselected -> RequestDetails.Prompt
    is NetworkSelection.Request -> transactions.firstOrNull { it.id == selection.id }?.let { RequestDetails.Ready(it) } ?: RequestDetails.Prompt
}
internal fun durationText(status: Status): String = when (status) {
    Status.InProgress -> "—"
    is Status.Completed -> status.durationMillis.toString()
    is Status.Failed -> status.durationMillis.toString()
}
internal fun statusText(status: Status): String = when (status) {
    Status.InProgress -> "…"
    is Status.Completed -> status.code.toString()
    is Status.Failed -> "× ${status.error}"
}
internal fun requestDetails(tx: NetworkTransaction, body: String = bodyText(tx.requestBody)): String =
    "${tx.method} ${tx.url}\n" + tx.requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" } + "\n\n" + body
internal fun responseDetails(tx: NetworkTransaction, body: String = bodyText(tx.responseBody)): String =
    statusText(tx.status) + "\n" + tx.responseHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" } + "\n\n" + body
internal fun logRows(entries: List<LogEntry>): List<FeedRow> = entries.asReversed().map {
    FeedRow(it.id, it.message, "${it.timestampMillis} · ${it.level} · ${it.tag}")
}
internal fun eventRows(entries: List<DiagnosticEvent>): List<FeedRow> = entries.asReversed().map {
    FeedRow(it.id, it.message, "${it.timestampMillis} · ${it.category}")
}

private fun bodyText(body: CapturedBody) = when (body) {
    is CapturedBody.Text -> body.value
    else -> "—"
}
