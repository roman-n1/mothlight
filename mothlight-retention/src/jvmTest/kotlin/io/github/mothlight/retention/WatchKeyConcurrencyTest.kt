package io.github.mothlight.retention

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class WatchKeyConcurrencyTest {
    @Test fun registrationRetryRekeysItsWeakReference() = runBlocking {
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val barrier = CyclicBarrier(2)
        val created = AtomicInteger()
        val observed = ConcurrentLinkedQueue<WatchKey>()
        val backend = object : RetentionBackend {
            override val support = RetentionSupport.MANAGED_HEAP
            override fun reference(target: Any): WeakHandle = error("Keyed overload required")
            override fun reference(target: Any, key: WatchKey): WeakHandle {
                if (created.incrementAndGet() <= 2) barrier.await(10, TimeUnit.SECONDS)
                return WeakHandle { observed.add(key); true }
            }
            override fun requestCollection() = Unit
        }
        val watcher = RetentionWatcher(scope, backend, graceMillis = 0, intervalMillis = Long.MAX_VALUE)
        try {
            watcher.setEnabled(true)
            val ids = (1..2).map { async(dispatcher) { (watcher.expectReleased(Any(), "same", "disposed") as WatchResult.Accepted).id } }.awaitAll()
            watcher.checkNow()
            assertEquals(ids.toSet(), observed.map { it.watchId }.toSet())
            assertEquals(setOf(watcher.analysisRequest().sessionId), observed.map { it.sessionId }.toSet())
        } finally { watcher.close(); scope.cancel(); dispatcher.close() }
    }
}
