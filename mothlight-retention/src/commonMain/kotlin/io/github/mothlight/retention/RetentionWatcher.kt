package io.github.mothlight.retention

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.TimeSource
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Observations are not proof of a leak and contain no target instances. */
enum class RetentionSupport { MANAGED_HEAP, KOTLIN_NATIVE, UNSUPPORTED }
enum class WatcherMode { DISABLED, RUNNING, UNSUPPORTED, CLOSED }
enum class RetentionState { WATCHING, RETAINED, COLLECTED }
enum class RetentionCheck { NOT_CHECKED, OBSERVED, COLLECTION_REQUESTED, FAILED }
data class RetentionEntry(val id: Long, val label: String, val reason: String, val state: RetentionState, val watchedAtMillis: Long, val ageMillis: Long)
data class RetentionSnapshot(
    val mode: WatcherMode,
    val support: RetentionSupport,
    val entries: List<RetentionEntry> = emptyList(),
    val retainedCount: Int = 0,
    val evictedCount: Long = 0,
    val lastCheck: RetentionCheck = RetentionCheck.NOT_CHECKED,
)
sealed interface WatchResult {
    data class Accepted(val id: Long) : WatchResult
    data object Disabled : WatchResult
    data object Unsupported : WatchResult
    data object Closed : WatchResult
}

/** Must not retain or expose the observed target. */
fun interface WeakHandle { fun isAlive(): Boolean }
interface RetentionBackend {
    val support: RetentionSupport
    fun reference(target: Any): WeakHandle
    fun reference(target: Any, key: WatchKey): WeakHandle = reference(target)
    /** Best effort on JVM/Android; completion is not a leak verdict. */
    fun requestCollection()
}
expect fun platformRetentionBackend(): RetentionBackend

/** Own in the host scope, not in a selected tool's composition. Starts disabled. */
class RetentionWatcher(
    scope: CoroutineScope,
    private val backend: RetentionBackend = platformRetentionBackend(),
    private val capacity: Int = 200,
    private val graceMillis: Long = 5_000,
    private val intervalMillis: Long = 1_000,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMillis: () -> Long = monotonicClock(),
) {
    init {
        require(capacity > 0) { "Retention capacity must be positive" }
        require(graceMillis >= 0) { "Retention grace period must not be negative" }
        require(intervalMillis > 0) { "Retention interval must be positive" }
    }
    private val sessionId = List(4) { Random.nextLong().toString(16) }.joinToString("-")
    private val support = backend.support
    private val state = MutableStateFlow(TrackingState(
        mode = if (support == RetentionSupport.UNSUPPORTED) WatcherMode.UNSUPPORTED else WatcherMode.DISABLED,
    ))
    private val checks = Mutex()
    val snapshot: RetentionSnapshot get() = state.value.publicSnapshot(support)
    val snapshots: Flow<RetentionSnapshot> = state.map { it.publicSnapshot(support) }.distinctUntilChanged()
    private val worker = scope.launch(start = CoroutineStart.LAZY) {
        while (isActive) {
            state.first { current -> current.mode == WatcherMode.RUNNING && current.tracked.any { it.handle != null } }
            delay(intervalMillis)
            checkNow()
        }
    }
    init {
        worker.invokeOnCompletion { markClosed() }
        worker.start()
    }

    fun setEnabled(enabled: Boolean) {
        state.update { current ->
            when {
                current.mode == WatcherMode.CLOSED -> current
                support == RetentionSupport.UNSUPPORTED -> current
                enabled -> current.copy(mode = WatcherMode.RUNNING)
                else -> current.reset(WatcherMode.DISABLED)
            }
        }
    }

    fun expectReleased(target: Any, label: String, reason: String): WatchResult {
        var handle: WeakHandle? = null
        var handleId: Long? = null
        val watchedAt = nowMillis()
        while (true) {
            val current = state.value
            when (current.mode) {
                WatcherMode.DISABLED -> return WatchResult.Disabled
                WatcherMode.UNSUPPORTED -> return WatchResult.Unsupported
                WatcherMode.CLOSED -> return WatchResult.Closed
                WatcherMode.RUNNING -> Unit
            }
            // Create the weak handle before any deferred work. Neither CAS closure nor worker captures target.
            val weak = if (handleId == current.nextId) checkNotNull(handle) else
                backend.reference(target, WatchKey(sessionId, current.nextId)).also { handle = it; handleId = current.nextId }
            val entry = RetentionEntry(current.nextId, label.take(256), reason.take(512), RetentionState.WATCHING, watchedAt, 0)
            val expanded = current.tracked + Tracked(entry, weak)
            val next = current.copy(
                nextId = current.nextId + 1,
                tracked = expanded.takeLast(capacity),
                evicted = current.evicted + (expanded.size - capacity).coerceAtLeast(0),
            )
            if (state.compareAndSet(current, next)) return WatchResult.Accepted(entry.id)
        }
    }

    suspend fun checkNow(requestCollection: Boolean = false) = checks.withLock {
        val before = state.value
        if (before.mode != WatcherMode.RUNNING || before.tracked.none { it.handle != null }) return@withLock
        val changes = try {
            withContext(dispatcher) {
                if (requestCollection) backend.requestCollection()
                val now = nowMillis()
                before.tracked.associate { tracked ->
                    val entry = tracked.entry
                    val handle = tracked.handle
                    val changed = if (handle == null) tracked else {
                        val age = (now - entry.watchedAtMillis).coerceAtLeast(0)
                        when {
                            !handle.isAlive() -> Tracked(entry.copy(state = RetentionState.COLLECTED, ageMillis = age), null)
                            age >= graceMillis -> tracked.copy(entry = entry.copy(state = RetentionState.RETAINED, ageMillis = age))
                            else -> tracked.copy(entry = entry.copy(ageMillis = age))
                        }
                    }
                    entry.id to changed
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.update { current ->
                if (current.epoch == before.epoch && current.mode == WatcherMode.RUNNING) current.copy(lastCheck = RetentionCheck.FAILED)
                else current
            }
            return@withLock
        }
        currentCoroutineContext().ensureActive()
        state.update { current ->
            if (current.epoch != before.epoch || current.mode != WatcherMode.RUNNING) current
            else current.copy(
                tracked = current.tracked.map { changes[it.entry.id] ?: it },
                lastCheck = if (requestCollection) RetentionCheck.COLLECTION_REQUESTED else RetentionCheck.OBSERVED,
            )
        }
    }

    /** Immutable metadata only; never carries a strong reference to a watched object. */
    fun analysisRequest(): RetentionAnalysisRequest {
        val current = state.value
        val entries = if (current.mode == WatcherMode.RUNNING) current.tracked.map { it.entry }.filter { it.state == RetentionState.RETAINED } else emptyList()
        return RetentionAnalysisRequest(sessionId, current.epoch, entries)
    }

    fun isCurrent(request: RetentionAnalysisRequest): Boolean {
        val current = state.value
        return request.sessionId == sessionId && request.generation == current.epoch && current.mode == WatcherMode.RUNNING
    }

    fun clear() { state.update { it.reset(it.mode) } }
    fun close() { markClosed(); worker.cancel() }
    private fun markClosed() { state.update { if (it.mode == WatcherMode.CLOSED) it else it.reset(WatcherMode.CLOSED) } }

}
private fun monotonicClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}

private data class Tracked(val entry: RetentionEntry, val handle: WeakHandle?)
private data class TrackingState(
    val mode: WatcherMode,
    val tracked: List<Tracked> = emptyList(),
    val epoch: Long = 0,
    val nextId: Long = 1,
    val evicted: Long = 0,
    val lastCheck: RetentionCheck = RetentionCheck.NOT_CHECKED,
)
private fun TrackingState.reset(mode: WatcherMode) = copy(mode = mode, tracked = emptyList(), epoch = epoch + 1, evicted = 0, lastCheck = RetentionCheck.NOT_CHECKED)
private fun TrackingState.publicSnapshot(support: RetentionSupport) = RetentionSnapshot(
    mode, support, tracked.map { it.entry }, tracked.count { it.entry.state == RetentionState.RETAINED }, evicted, lastCheck,
)
