package io.github.mothlight.retention

import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.test.*

class JvmRetentionTest {
    @Test fun backendUsesManagedHeapAndDoesNotLoseStronglyRootedControl() {
        val backend = platformRetentionBackend()
        assertEquals(RetentionSupport.MANAGED_HEAP, backend.support)
        val root = arrayOf("keep alive")
        val handle = backend.reference(root)
        backend.requestCollection()
        assertTrue(handle.isAlive())
        assertEquals("keep alive", root.single())
    }
    @Test fun concurrentRegistrationPreservesUniqueIdsAndAllEntries() = runBlocking {
        val dispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val backend = object : RetentionBackend {
            override val support = RetentionSupport.MANAGED_HEAP
            override fun reference(target: Any) = WeakHandle { true }
            override fun requestCollection() = Unit
        }
        val watcher = RetentionWatcher(scope, backend, capacity = 400)
        try {
            watcher.setEnabled(true)
            val ids = (1..4).map { async(dispatcher) {
                (1..100).map { assertIs<WatchResult.Accepted>(watcher.expectReleased(Any(), "same", "released")).id }
            } }.awaitAll().flatten()
            assertEquals(400, ids.toSet().size)
            assertEquals(ids.toSet(), watcher.snapshot.entries.map { it.id }.toSet())
            assertEquals(0, watcher.snapshot.evictedCount)
        } finally { watcher.close(); scope.cancel(); dispatcher.close() }
    }
}
