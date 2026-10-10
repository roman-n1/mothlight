package io.github.mothlight.compose

import io.github.mothlight.*
import kotlin.test.*

class DiagnosticsMappersTest {
    @Test fun startupBarsUseTimelineProportionsAndHandleZeroDuration() {
        val rows = startupRows(listOf(StartupSpan(1, "database", 100, 50), StartupSpan(2, "graph", 150, 150), StartupSpan(3, "frame", 300, 0)))
        assertEquals(listOf("0", "50", "200"), rows.map { it.offsetMillis })
        assertEquals(listOf("50", "150", "0"), rows.map { it.durationMillis })
        assertEquals(listOf(0.25f, 0.75f, 0f), rows.map { it.fraction })
        assertEquals(0f, startupRows(listOf(StartupSpan(1, "only", 0, 0))).single().fraction)
        assertTrue(startupRows(emptyList()).isEmpty())
    }
    @Test fun lifecyclePreservesNegativeObservedBalanceAndFiltersHistory() {
        val snapshot = LifecycleSnapshot(
            listOf(LifecycleEntry(1, "Screen", LifecycleEvent.CREATED, 1), LifecycleEntry(2, "Worker", LifecycleEvent.DESTROYED, 2), LifecycleEntry(3, "Screen", LifecycleEvent.DESTROYED, 3)),
            listOf(LifecycleCount("Worker", 0, 1), LifecycleCount("Screen", 3, 1)),
        )
        assertEquals(listOf(LifecycleRow("Screen", "3", "1", "2"), LifecycleRow("Worker", "0", "1", "-1")), lifecycleRows(snapshot, ""))
        assertEquals(listOf(3L, 1L), lifecycleEvents(snapshot, "SCREEN").map { it.id })
        assertEquals(listOf("Worker"), lifecycleRows(snapshot, "work").map { it.kind })
    }
    @Test fun navigationIsNewestFirstAndInvariantsSortBySeverityThenCount() {
        val entries = listOf(NavigationEntry(1, "Home", NavigationDirection.RESET, 1), NavigationEntry(2, "Details", NavigationDirection.PUSH, 2), NavigationEntry(3, "Home", NavigationDirection.POP, 3))
        assertEquals(listOf(3L, 1L), navigationRows(entries, "HOME").map { it.id })
        val snapshot = InvariantSnapshot(counters = listOf(
            InvariantStat("frequent", 100, InvariantSeverity.INFO, InvariantSeverity.INFO, 1, CapturedBody.NotCaptured, ScreenContext.Unspecified),
            InvariantStat("second", 1, InvariantSeverity.ERROR, InvariantSeverity.WARNING, 2, CapturedBody.NotCaptured, ScreenContext.Named("Details")),
            InvariantStat("first", 2, InvariantSeverity.ERROR, InvariantSeverity.ERROR, 3, CapturedBody.NotCaptured, ScreenContext.Named("Home")),
        ))
        assertEquals(listOf("first", "second", "frequent"), invariantRows(snapshot, "").map { it.rule })
        assertEquals(listOf("second"), invariantRows(snapshot, "DETAILS").map { it.rule })
    }
}
