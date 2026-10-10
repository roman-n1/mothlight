package io.github.mothlight.retention

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.TimeSource

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
    val snapshot: RetentionSnapshot get() = RetentionSnapshot(WatcherMode.DISABLED, backend.support)
    val snapshots: Flow<RetentionSnapshot> = flowOf(snapshot)
    fun setEnabled(enabled: Boolean) = Unit
    fun expectReleased(target: Any, label: String, reason: String): WatchResult = WatchResult.Disabled
    suspend fun checkNow(requestCollection: Boolean = false) = Unit
    fun clear() = Unit
    fun close() = Unit
}
private fun monotonicClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
