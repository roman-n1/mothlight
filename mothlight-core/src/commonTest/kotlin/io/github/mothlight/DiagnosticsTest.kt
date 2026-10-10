package io.github.mothlight

import kotlin.test.*

class DiagnosticsTest {
    @Test fun startupSortsMilestonesAndRecomputesIntervalsAfterEviction() {
        val trace = StartupTrace(3)
        trace.mark("frame", 300)
        trace.mark("database", 100)
        trace.mark("graph", 150)
        assertEquals(listOf("database", "graph", "frame"), trace.spans.value.map { it.stage })
        assertEquals(listOf(50L, 150L, 0L), trace.spans.value.map { it.durationMillis })
        trace.mark("ready", 400)
        assertEquals(listOf("graph", "frame", "ready"), trace.spans.value.map { it.stage })
        assertEquals(listOf(150L, 100L, 0L), trace.spans.value.map { it.durationMillis })
        val lastId = trace.spans.value.maxOf { it.id }
        trace.clear()
        assertTrue(trace.spans.value.isEmpty())
        trace.mark("repeat", 0); trace.mark("repeat", 0)
        assertEquals(2, trace.spans.value.map { it.id }.distinct().size)
        assertTrue(trace.spans.value.all { it.id > lastId })
        assertEquals(listOf(0L, 0L), trace.spans.value.map { it.durationMillis })
    }
    @Test fun lifecycleCountersSurviveFeedEvictionAndEvictLeastRecentlyUsedKind() {
        val recorder = LifecycleRecorder(2, 2)
        recorder.record("Screen", LifecycleEvent.CREATED, 1)
        recorder.record("Screen", LifecycleEvent.CREATED, 2)
        recorder.record("Screen", LifecycleEvent.DESTROYED, 3)
        assertEquals(LifecycleCount("Screen", 2, 1), recorder.snapshot.value.counters.single())
        assertEquals(listOf(2L, 3L), recorder.snapshot.value.events.map { it.timestampMillis })
        recorder.record("Cache", LifecycleEvent.CREATED, 4)
        recorder.record("Screen", LifecycleEvent.DESTROYED, 5)
        recorder.record("Worker", LifecycleEvent.DESTROYED, 6)
        assertEquals(listOf(LifecycleCount("Screen", 2, 2), LifecycleCount("Worker", 0, 1)), recorder.snapshot.value.counters)
        recorder.clear()
        assertEquals(LifecycleSnapshot(), recorder.snapshot.value)
    }
    @Test fun navigationRetainsRepeatedDestinationsWithUniqueIdsAndAllDirections() {
        val breadcrumbs = NavigationBreadcrumbs(3)
        breadcrumbs.record("Home", NavigationDirection.RESET, 1)
        breadcrumbs.record("Details", NavigationDirection.PUSH, 2)
        breadcrumbs.record("Details", NavigationDirection.REPLACE, 3)
        breadcrumbs.record("Home", NavigationDirection.POP, 4)
        assertEquals(listOf(NavigationDirection.PUSH, NavigationDirection.REPLACE, NavigationDirection.POP), breadcrumbs.entries.value.map { it.direction })
        assertEquals(listOf("Details", "Details", "Home"), breadcrumbs.entries.value.map { it.destination })
        assertEquals(3, breadcrumbs.entries.value.map { it.id }.distinct().size)
        val lastId = breadcrumbs.entries.value.last().id
        breadcrumbs.clear()
        breadcrumbs.record("Home", NavigationDirection.RESET, 5)
        assertTrue(breadcrumbs.entries.value.single().id > lastId)
    }
    @Test fun invariantCountersKeepPeakSeverityAndMaskSamplesBeforePublication() {
        val recorder = InvariantRecorder(2, 2, Redactor(captureJsonBodies = true, additionalSensitiveKeys = setOf("account")))
        recorder.record("order", InvariantSeverity.ERROR, 1, """{"token":"secret","account":"private","n":1}""", ScreenContext.Named("Details"))
        val sample = """{"token":"***","account":"***","n":1}"""
        assertEquals(CapturedBody.Text(sample), recorder.snapshot.value.events.single().sample)
        assertEquals(CapturedBody.Text(sample), recorder.snapshot.value.counters.single().lastSample)
        recorder.record("order", InvariantSeverity.WARNING, 2, screen = ScreenContext.Named("Home"))
        recorder.record("cache", InvariantSeverity.INFO, 3)
        assertEquals(2, recorder.snapshot.value.events.size)
        assertEquals(InvariantStat("order", 2, InvariantSeverity.ERROR, InvariantSeverity.WARNING, 2, CapturedBody.NotCaptured, ScreenContext.Named("Home")), recorder.snapshot.value.counters.first())
        recorder.record("order", InvariantSeverity.INFO, 4)
        recorder.record("third", InvariantSeverity.INFO, 5)
        assertEquals(listOf("order", "third"), recorder.snapshot.value.counters.map { it.rule })
        recorder.clear()
        assertEquals(InvariantSnapshot(), recorder.snapshot.value)
        val defaults = InvariantRecorder()
        defaults.record("test", InvariantSeverity.INFO, 1, """{"data":"private"}""")
        assertEquals(CapturedBody.NotCaptured, defaults.snapshot.value.events.single().sample)
    }
    @Test fun allDiagnosticsFollowOwnerGateAndClearWithoutSharingState() {
        val first = Mothlight()
        val second = Mothlight()
        fun record(m: Mothlight) {
            m.startup.mark("ready", 1)
            m.lifecycle.record("Screen", LifecycleEvent.CREATED, 1)
            m.navigation.record("Home", NavigationDirection.PUSH, 1)
            m.invariants.record("rule", InvariantSeverity.WARNING, 1, screen = ScreenContext.Named("Explicit"))
        }
        record(first)
        assertTrue(first.startup.spans.value.isEmpty())
        assertTrue(first.lifecycle.snapshot.value.events.isEmpty())
        assertTrue(first.navigation.entries.value.isEmpty())
        assertTrue(first.invariants.snapshot.value.events.isEmpty())
        first.setEnabled(true); record(first)
        assertEquals(1, first.startup.spans.value.size)
        assertEquals(1, first.lifecycle.snapshot.value.counters.size)
        assertEquals(1, first.navigation.entries.value.size)
        assertEquals(1, first.invariants.snapshot.value.counters.size)
        first.setEnabled(false); record(first)
        assertEquals(1L, first.lifecycle.snapshot.value.counters.single().created)
        assertEquals(1L, first.invariants.snapshot.value.counters.single().count)
        second.setEnabled(true); record(second)
        first.clear()
        assertTrue(first.startup.spans.value.isEmpty())
        assertEquals(LifecycleSnapshot(), first.lifecycle.snapshot.value)
        assertTrue(first.navigation.entries.value.isEmpty())
        assertEquals(InvariantSnapshot(), first.invariants.snapshot.value)
        assertEquals(ScreenContext.Named("Explicit"), second.invariants.snapshot.value.counters.single().lastScreen)
        second.navigation.record("Different", NavigationDirection.PUSH, 2)
        assertEquals(ScreenContext.Named("Explicit"), second.invariants.snapshot.value.counters.single().lastScreen)
    }
    @Test fun invalidBoundsFailAtConstruction() {
        assertFailsWith<IllegalArgumentException> { StartupTrace(0) }
        assertFailsWith<IllegalArgumentException> { LifecycleRecorder(kindCapacity = 0) }
        assertFailsWith<IllegalArgumentException> { NavigationBreadcrumbs(-1) }
        assertFailsWith<IllegalArgumentException> { InvariantRecorder(ruleCapacity = 0) }
    }
}
