package io.github.mothlight

import kotlinx.coroutines.flow.*

/** Instance-owned HTTP recorder adapted from the original Debug panel. */
class NetworkInspector(
    private val capacity: Int = 200,
    val redactor: Redactor = Redactor(),
) {
    init { require(capacity > 0) }
    private val mutableEnabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()
    private val _transactions = MutableStateFlow<List<NetworkTransaction>>(emptyList())
    val transactions: StateFlow<List<NetworkTransaction>> = _transactions.asStateFlow()
    private val sequenceCounter = MutableStateFlow(0L)
    fun setEnabled(value: Boolean) { mutableEnabled.value = value }
    fun clear() { _transactions.value = emptyList() }
    /** Record a start with an atomic, monotonically increasing ID, even across clear(). */
    fun recordStart(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: CapturedBody,
        now: Long,
    ): String {
        val seq = sequenceCounter.updateAndGet { it + 1 }
        val id = seq.toString()
        if (!enabled.value) return id
        val tx = NetworkTransaction(
            id = id,
            sequenceNumber = seq,
            method = method,
            url = redactor.url(url),
            requestHeaders = redactor.headers(headers),
            requestBody = redactor.body(body),
            responseHeaders = emptyMap(),
            responseBody = CapturedBody.NotCaptured,
            startedAtMillis = now,
            status = Status.InProgress,
        )
        _transactions.update { current -> (current + tx).takeLast(capacity) }
        return id
    }

    fun recordStart(method: String, url: String, headers: Map<String, String>, body: String, now: Long): String =
        recordStart(method, url, headers, CapturedBody.Text(body), now)

    fun recordSuccess(id: String, code: Int, headers: Map<String, String>, body: String, now: Long) =
        recordSuccess(id, code, headers, CapturedBody.Text(body), now)

    /** Complete only a retained request; late responses never resurrect evicted entries. */
    fun recordSuccess(
        id: String,
        code: Int,
        headers: Map<String, String>,
        body: CapturedBody,
        now: Long,
    ) {
        if (!enabled.value) return
        _transactions.update { current ->
            current.map { tx ->
                if (tx.id != id) return@map tx
                tx.copy(
                    responseHeaders = redactor.headers(headers),
                    responseBody = redactor.body(body).let { if (it == CapturedBody.NotCaptured) tx.responseBody else it },
                    status = Status.Completed(code, now - tx.startedAtMillis),
                )
            }
        }
    }

    /** The host must pass a scrubbed error label, not an exception message containing secrets. */
    fun recordFailure(id: String, error: String, now: Long) {
        if (!enabled.value) return
        _transactions.update { current ->
            current.map { tx ->
                if (tx.id != id) return@map tx
                tx.copy(
                    status = Status.Failed(error, now - tx.startedAtMillis),
                )
            }
        }
    }


}
