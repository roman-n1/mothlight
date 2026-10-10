package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InvariantSeverity { INFO, WARNING, ERROR }
data class InvariantEvent(val id: Long, val rule: String, val severity: InvariantSeverity, val timestampMillis: Long, val sample: String?, val screen: String?)
data class InvariantStat(val rule: String, val count: Long, val highestSeverity: InvariantSeverity, val lastSeverity: InvariantSeverity, val lastSeenAtMillis: Long, val lastSample: String?, val lastScreen: String?)
data class InvariantSnapshot(val events: List<InvariantEvent> = emptyList(), val counters: List<InvariantStat> = emptyList())

/** Observes violations; never throws for a reported violation or enforces the host's rules. */
class InvariantRecorder(private val capacity: Int = 200, private val ruleCapacity: Int = 100, private val redactor: Redactor = Redactor(), private val enabled: () -> Boolean = { true }) {
    private val state = MutableStateFlow(InvariantSnapshot())
    val snapshot = state.asStateFlow()
    fun record(rule: String, severity: InvariantSeverity, timestampMillis: Long, sampleJson: String? = null, screen: String? = null) { }
    fun clear() { state.value = InvariantSnapshot() }
}
