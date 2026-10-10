package io.github.mothlight.retention

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex

/** Key stored alongside a weak reference; session identity prevents collisions between watchers. */
data class WatchKey(val sessionId: String, val watchId: Long)
data class RetentionAnalysisRequest(val sessionId: String, val generation: Long, val entries: List<RetentionEntry>)
enum class FindingStatus { STRONG_PATH, NOT_IN_DUMP, NO_STRONG_PATH }
enum class AnalysisFailure { CAPTURE, DUMP_TOO_LARGE, ANALYSIS, CLEANUP }
enum class HeapReferenceKind { INSTANCE_FIELD, STATIC_FIELD, LOCAL, ARRAY_ENTRY }
data class RetainingReference(val ownerClass: String, val referenceName: String, val kind: HeapReferenceKind)
/** A path in one snapshot is evidence of retention, not proof that the application's lifetime contract is correct. */
data class RetainingPath(val gcRootType: String, val references: List<RetainingReference>, val targetClass: String, val signature: String)
sealed interface RetentionFinding {
    val watchId: Long
    val status: FindingStatus
    data class StrongPath(override val watchId: Long, val path: RetainingPath) : RetentionFinding {
        override val status = FindingStatus.STRONG_PATH
    }
    data class NotInDump(override val watchId: Long) : RetentionFinding {
        override val status = FindingStatus.NOT_IN_DUMP
    }
    data class NoStrongPath(override val watchId: Long) : RetentionFinding {
        override val status = FindingStatus.NO_STRONG_PATH
    }
}
sealed interface RetentionAnalysisResult {
    data class Report(val findings: List<RetentionFinding>) : RetentionAnalysisResult
    data class Failed(val failure: AnalysisFailure) : RetentionAnalysisResult
    data object Unsupported : RetentionAnalysisResult
}
fun interface RetentionAnalyzer {
    /** Explicit, potentially expensive capture. Must clean up temporary data even on cancellation. */
    suspend fun analyze(request: RetentionAnalysisRequest): RetentionAnalysisResult
}
/** Default behavior for hosts without a heap analyzer. */
object UnsupportedRetentionAnalyzer : RetentionAnalyzer {
    override suspend fun analyze(request: RetentionAnalysisRequest) = RetentionAnalysisResult.Unsupported
}
sealed interface RetentionAnalysisState {
    data object Idle : RetentionAnalysisState
    data class Running(val request: RetentionAnalysisRequest) : RetentionAnalysisState
    data class Finished(val request: RetentionAnalysisRequest, val result: RetentionAnalysisResult) : RetentionAnalysisState
}

/** Single flight; the caller's coroutine owns cancellation. Clear/pause/close invalidate old reports. */
class RetentionAnalysisController(private val watcher: RetentionWatcher, private val analyzer: RetentionAnalyzer) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<RetentionAnalysisState>(RetentionAnalysisState.Idle)
    val snapshot: RetentionAnalysisState get() = current(state.value)
    val states: Flow<RetentionAnalysisState> = combine(state, watcher.snapshots) { value, _ -> current(value) }.distinctUntilChanged()

    suspend fun analyze() {
        if (!mutex.tryLock()) return
        try {
            val request = watcher.analysisRequest()
            if (request.entries.isEmpty()) { state.value = RetentionAnalysisState.Idle; return }
            state.value = RetentionAnalysisState.Running(request)
            val result = try { analyzer.analyze(request) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { RetentionAnalysisResult.Failed(AnalysisFailure.ANALYSIS) }
            currentCoroutineContext().ensureActive()
            state.value = if (watcher.isCurrent(request)) RetentionAnalysisState.Finished(request, result) else RetentionAnalysisState.Idle
        } catch (cancelled: CancellationException) {
            state.value = RetentionAnalysisState.Idle
            throw cancelled
        } finally { mutex.unlock() }
    }

    private fun current(value: RetentionAnalysisState): RetentionAnalysisState = when (value) {
        RetentionAnalysisState.Idle -> value
        is RetentionAnalysisState.Running -> if (watcher.isCurrent(value.request)) value else RetentionAnalysisState.Idle
        is RetentionAnalysisState.Finished -> if (watcher.isCurrent(value.request)) value else RetentionAnalysisState.Idle
    }
}
