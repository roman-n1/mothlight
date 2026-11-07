package io.github.mothlight

import kotlinx.coroutines.flow.StateFlow

/** Own one instance in your application graph. No process-global registries or collectors. */
class Mothlight(capacity: Int = 200, redactor: Redactor = Redactor(), memoryOverlayStore: MemoryOverlayStore = TransientMemoryOverlayStore) {
    val memoryOverlay = MemoryOverlaySettings(memoryOverlayStore)
    val network = NetworkInspector(capacity, redactor)
    val enabled: StateFlow<Boolean> = network.enabled
    val logs = ProbeFeed<LogEntry>(capacity) { enabled.value }
    val sql = SqlTraceRecorder(capacity, enabled = { enabled.value })
    val startup = StartupTrace(capacity) { enabled.value }
    fun setEnabled(value: Boolean) { network.setEnabled(value) }
    fun clear() {
        network.clear()
        logs.clear()
        sql.clear()
        startup.clear()
    }
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** Messages must be scrubbed by the host before recording. IDs must be unique within a feed. */
data class LogEntry(val id: String, val timestampMillis: Long, val level: LogLevel, val tag: String, val message: String)
