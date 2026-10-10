package io.github.mothlight

/** A recorded request. Headers and optional bodies are scrubbed before publication. */
data class NetworkTransaction(
    val id: String,
    val sequenceNumber: Long,
    val method: String,
    val url: String,
    val requestHeaders: Map<String, String>,
    val requestBody: CapturedBody,
    val responseHeaders: Map<String, String>,
    val responseBody: CapturedBody,
    val startedAtMillis: Long,
    val status: Status,
)

/** A response with any HTTP code is Completed; transport errors are Failed. */
sealed interface Status {
    data object InProgress : Status
    data class Completed(val code: Int, val durationMillis: Long) : Status
    data class Failed(val error: String, val durationMillis: Long) : Status
}
