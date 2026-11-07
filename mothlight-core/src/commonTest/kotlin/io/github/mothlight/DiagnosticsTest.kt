package io.github.mothlight

import kotlin.test.*

class DiagnosticsTest {
    @Test fun startupMilestonesFollowOwnerGateAndClearIndependently() {
        val first = Mothlight()
        val second = Mothlight()
        first.startup.mark("disabled", 1)
        assertTrue(first.startup.spans.value.isEmpty())
        first.setEnabled(true)
        first.startup.mark("ready", 2)
        first.setEnabled(false)
        first.startup.mark("paused", 3)
        assertEquals(listOf("ready"), first.startup.spans.value.map { it.stage })
        second.setEnabled(true)
        second.startup.mark("other", 4)
        first.clear()
        assertTrue(first.startup.spans.value.isEmpty())
        assertEquals(listOf("other"), second.startup.spans.value.map { it.stage })
        assertFailsWith<IllegalArgumentException> { StartupTrace(0) }
    }

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
}
