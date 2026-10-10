package io.github.mothlight

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DiagnosticsConcurrencyTest {
    @Test fun simultaneousWritersDoNotLoseMilestonesCountersOrHistory() {
        val startup = StartupTrace(400)
        val lifecycle = LifecycleRecorder(400)
        val invariants = InvariantRecorder(400)
        val navigation = NavigationBreadcrumbs(400)
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val jobs = (0 until 4).map { worker -> executor.submit {
                start.await()
                repeat(100) { index ->
                    val timestamp = (worker * 100 + index).toLong()
                    startup.mark("stage", timestamp)
                    lifecycle.record("Worker", LifecycleEvent.CREATED, timestamp)
                    invariants.record("rule", InvariantSeverity.WARNING, timestamp)
                    navigation.record("Home", NavigationDirection.PUSH, timestamp)
                }
            } }
            start.countDown()
            jobs.forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals((0L until 400L).toList(), startup.spans.value.map { it.startedAtMillis })
            assertEquals(List(399) { 1L } + 0L, startup.spans.value.map { it.durationMillis })
            assertEquals(400L, lifecycle.snapshot.value.counters.single().created)
            assertEquals(400L, invariants.snapshot.value.counters.single().count)
            assertEquals(400, lifecycle.snapshot.value.events.map { it.id }.distinct().size)
            assertEquals(400, invariants.snapshot.value.events.map { it.id }.distinct().size)
            assertEquals(400, navigation.entries.value.map { it.id }.distinct().size)
        } finally { executor.shutdownNow() }
    }
}
