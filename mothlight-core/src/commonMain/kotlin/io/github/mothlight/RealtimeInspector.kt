package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FrameDirection { INCOMING, OUTGOING }
enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING, FAILED }
data class RealtimeFrame(val id: Long, val channel: String, val direction: FrameDirection, val payload: String?, val timestampMillis: Long)
data class ConnectionEvent(val id: Long, val channel: String, val state: ConnectionState, val timestampMillis: Long)

/** Transport-independent passive observer; never reads a socket or changes its lifecycle. */
class RealtimeInspector(capacity: Int = 200, private val redactor: Redactor = Redactor(), private val enabled: () -> Boolean = { true }) {
    private val mutableFrames = MutableStateFlow<List<RealtimeFrame>>(emptyList())
    private val mutableConnections = MutableStateFlow<List<ConnectionEvent>>(emptyList())
    val frames = mutableFrames.asStateFlow()
    val connections = mutableConnections.asStateFlow()
    fun record(channel: String, direction: FrameDirection, payload: String, timestampMillis: Long) { }
    fun connection(channel: String, state: ConnectionState, timestampMillis: Long) { }
    fun clear() { mutableFrames.value = emptyList(); mutableConnections.value = emptyList() }
}
