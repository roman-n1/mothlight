package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

enum class InvariantSeverity { INFO, WARNING, ERROR }
data class InvariantEvent(val id: Long, val rule: String, val severity: InvariantSeverity, val timestampMillis: Long, val sample: CapturedBody, val screen: ScreenContext)
data class InvariantStat(val rule: String, val count: Long, val highestSeverity: InvariantSeverity, val lastSeverity: InvariantSeverity, val lastSeenAtMillis: Long, val lastSample: CapturedBody, val lastScreen: ScreenContext)
data class InvariantSnapshot(val events: List<InvariantEvent> = emptyList(), val counters: List<InvariantStat> = emptyList())

/** Observes violations; never throws for a reported violation or enforces the host's rules. */
class InvariantRecorder(private val capacity: Int = 200, private val ruleCapacity: Int = 100, private val redactor: Redactor = Redactor(), private val enabled: () -> Boolean = { true }) {
    init { require(capacity > 0); require(ruleCapacity > 0) }
    private val sequence = MutableStateFlow(0L)
    private val state = MutableStateFlow(InvariantSnapshot())
    val snapshot = state.asStateFlow()
    fun record(rule: String, severity: InvariantSeverity, timestampMillis: Long, sampleJson: CapturedBody = CapturedBody.NotCaptured, screen: ScreenContext = ScreenContext.Unspecified) {
        if (!enabled()) return
        val label = rule.take(256)
        val sample = redactor.body(sampleJson)
        val screenLabel = when (screen) {
            ScreenContext.Unspecified -> screen
            is ScreenContext.Named -> ScreenContext.Named(screen.value.take(256))
        }
        val entry = InvariantEvent(sequence.updateAndGet { it + 1 }, label, severity, timestampMillis, sample, screenLabel)
        state.update { current ->
            val previous = current.counters.firstOrNull { it.rule == label }
                ?: InvariantStat(label, 0, severity, severity, timestampMillis, CapturedBody.NotCaptured, ScreenContext.Unspecified)
            val next = InvariantStat(label, previous.count + 1,
                maxOf(previous.highestSeverity, severity), severity, timestampMillis, sample, screenLabel)
            InvariantSnapshot((current.events + entry).takeLast(capacity),
                (current.counters.filterNot { it.rule == label } + next).takeLast(ruleCapacity))
        }
    }
    fun record(rule: String, severity: InvariantSeverity, timestampMillis: Long, sampleJson: String, screen: ScreenContext = ScreenContext.Unspecified) =
        record(rule, severity, timestampMillis, CapturedBody.Text(sampleJson), screen)
    fun clear() { state.value = InvariantSnapshot() }
}
