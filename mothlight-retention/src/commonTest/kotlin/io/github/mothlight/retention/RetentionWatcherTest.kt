package io.github.mothlight.retention

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

internal class FakeHandle(var alive: Boolean = true) : WeakHandle { override fun isAlive() = alive }
internal class FakeBackend(override val support: RetentionSupport = RetentionSupport.MANAGED_HEAP) : RetentionBackend {
    val handles = mutableListOf<FakeHandle>()
    var collections = 0
    var onCollection: () -> Unit = {}
    override fun reference(target: Any) = FakeHandle().also { handles += it }
    override fun requestCollection() { collections++; onCollection() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RetentionWatcherTest {
    @Test fun backgroundObservationFindsDeadlineAndLaterCollectionWithoutUiCollector() = runTest {
        val backend = FakeBackend()
        val watcher = RetentionWatcher(backgroundScope, backend, dispatcher = StandardTestDispatcher(testScheduler), nowMillis = { testScheduler.currentTime })
        assertEquals(WatchResult.Disabled, watcher.expectReleased(Any(), "Screen", "closed"))
        assertEquals(0, backend.handles.size)
        watcher.setEnabled(true)
        val id = assertIs<WatchResult.Accepted>(watcher.expectReleased(Any(), "Screen", "closed")).id
        runCurrent()
        advanceTimeBy(4999); runCurrent()
        assertEquals(RetentionState.WATCHING, watcher.snapshot.entries.single().state)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, watcher.snapshot.retainedCount)
        assertEquals(RetentionEntry(id, "Screen", "closed", RetentionState.RETAINED, 0, 5000), watcher.snapshot.entries.single())
        assertEquals(0, backend.collections)
        backend.handles.single().alive = false
        advanceTimeBy(1000); runCurrent()
        assertEquals(0, watcher.snapshot.retainedCount)
        assertEquals(RetentionState.COLLECTED, watcher.snapshot.entries.single().state)
        assertEquals(6000, watcher.snapshot.entries.single().ageMillis)
        watcher.close()
    }
    @Test fun collectedBeforeDeadlineAndDuplicateLabelsKeepDistinctIdentities() = runTest {
        val backend = FakeBackend()
        var now = 0L
        val watcher = RetentionWatcher(backgroundScope, backend, dispatcher = StandardTestDispatcher(testScheduler), nowMillis = { now })
        watcher.setEnabled(true)
        val first = assertIs<WatchResult.Accepted>(watcher.expectReleased(Any(), "Same", "first"))
        val second = assertIs<WatchResult.Accepted>(watcher.expectReleased(Any(), "Same", "second"))
        assertNotEquals(first.id, second.id)
        backend.handles.first().alive = false
        now = 4999
        watcher.checkNow()
        assertEquals(listOf(RetentionState.COLLECTED, RetentionState.WATCHING), watcher.snapshot.entries.map { it.state })
        now = 5000
        watcher.checkNow()
        assertEquals(1, watcher.snapshot.retainedCount)
        assertEquals(4999, watcher.snapshot.entries.first().ageMillis)
        assertEquals(RetentionCheck.OBSERVED, watcher.snapshot.lastCheck)
        watcher.close()
    }
    @Test fun boundsEvictOldestAndClearNeverReusesIds() = runTest {
        val watcher = RetentionWatcher(backgroundScope, FakeBackend(), capacity = 2, dispatcher = StandardTestDispatcher(testScheduler))
        watcher.setEnabled(true)
        val ids = (1..3).map { assertIs<WatchResult.Accepted>(watcher.expectReleased(Any(), "x".repeat(300), "y".repeat(600))).id }
        assertEquals(ids.takeLast(2), watcher.snapshot.entries.map { it.id })
        assertEquals(1, watcher.snapshot.evictedCount)
        assertEquals(256, watcher.snapshot.entries.first().label.length)
        assertEquals(512, watcher.snapshot.entries.first().reason.length)
        watcher.clear()
        assertTrue(watcher.snapshot.entries.isEmpty())
        assertEquals(0, watcher.snapshot.evictedCount)
        assertTrue(assertIs<WatchResult.Accepted>(watcher.expectReleased(Any(), "new", "closed")).id > ids.last())
        watcher.close()
    }
    @Test fun gcIsOneExplicitRequestPerBatchAndClearDuringCheckCannotResurrectObjects() = runTest {
        val backend = FakeBackend()
        var now = 0L
        val watcher = RetentionWatcher(backgroundScope, backend, dispatcher = StandardTestDispatcher(testScheduler), nowMillis = { now })
        watcher.setEnabled(true)
        repeat(20) { watcher.expectReleased(Any(), "Object", "disposed") }
        now = 5000
        watcher.checkNow(requestCollection = true)
        assertEquals(1, backend.collections)
        assertEquals(20, watcher.snapshot.retainedCount)
        assertEquals(RetentionCheck.COLLECTION_REQUESTED, watcher.snapshot.lastCheck)
        backend.onCollection = {
            watcher.clear()
            watcher.expectReleased(Any(), "new", "released during check")
        }
        watcher.checkNow(requestCollection = true)
        assertEquals(listOf("new"), watcher.snapshot.entries.map { it.label })
        assertEquals(RetentionState.WATCHING, watcher.snapshot.entries.single().state)
        assertEquals(0, watcher.snapshot.retainedCount)
        watcher.close()
    }
    @Test fun pauseCloseAndHostScopeCancellationStopTracking() = runTest {
        val backend = FakeBackend()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val watcher = RetentionWatcher(owner, backend, dispatcher = StandardTestDispatcher(testScheduler))
        watcher.setEnabled(true)
        watcher.expectReleased(Any(), "old", "released")
        watcher.setEnabled(false)
        assertTrue(watcher.snapshot.entries.isEmpty())
        watcher.checkNow(true)
        assertEquals(0, backend.collections)
        watcher.setEnabled(true)
        watcher.expectReleased(Any(), "new", "released")
        owner.cancel(); runCurrent()
        assertEquals(WatcherMode.CLOSED, watcher.snapshot.mode)
        assertTrue(watcher.snapshot.entries.isEmpty())
        watcher.setEnabled(true)
        assertEquals(WatchResult.Closed, watcher.expectReleased(Any(), "later", "closed"))
    }
    @Test fun unsupportedNeverLooksLikeCollectedAndInstancesAreIndependent() = runTest {
        val unsupported = RetentionWatcher(backgroundScope, FakeBackend(RetentionSupport.UNSUPPORTED))
        unsupported.setEnabled(true)
        assertEquals(WatcherMode.UNSUPPORTED, unsupported.snapshot.mode)
        assertEquals(WatchResult.Unsupported, unsupported.expectReleased(Any(), "x", "closed"))
        val first = RetentionWatcher(backgroundScope, FakeBackend())
        val other = RetentionWatcher(backgroundScope, FakeBackend())
        first.setEnabled(true)
        first.expectReleased(Any(), "x", "closed")
        assertEquals(1, first.snapshot.entries.size)
        assertTrue(other.snapshot.entries.isEmpty())
        first.close(); other.close(); unsupported.close()
    }
    @Test fun failedCheckPreservesObservationsAndCancellationPropagates() = runTest {
        val backend = FakeBackend()
        val watcher = RetentionWatcher(backgroundScope, backend, dispatcher = StandardTestDispatcher(testScheduler))
        watcher.setEnabled(true)
        watcher.expectReleased(Any(), "x", "closed")
        backend.onCollection = { error("backend unavailable") }
        watcher.checkNow(true)
        assertEquals(RetentionCheck.FAILED, watcher.snapshot.lastCheck)
        assertEquals(RetentionState.WATCHING, watcher.snapshot.entries.single().state)
        backend.onCollection = { throw CancellationException("cancel check") }
        assertFailsWith<CancellationException> { watcher.checkNow(true) }
        watcher.close()
    }
    @Test fun invalidLimitsAreRejected() = runTest {
        assertFailsWith<IllegalArgumentException> { RetentionWatcher(backgroundScope, FakeBackend(), capacity = 0) }
        assertFailsWith<IllegalArgumentException> { RetentionWatcher(backgroundScope, FakeBackend(), graceMillis = -1) }
        assertFailsWith<IllegalArgumentException> { RetentionWatcher(backgroundScope, FakeBackend(), intervalMillis = 0) }
    }
}
