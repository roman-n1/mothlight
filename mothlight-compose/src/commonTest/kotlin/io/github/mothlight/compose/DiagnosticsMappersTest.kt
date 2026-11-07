package io.github.mothlight.compose

import io.github.mothlight.*
import kotlin.test.*

class DiagnosticsMappersTest {
    @Test fun startupBarsUseTimelineProportionsAndHandleZeroDuration() {
        val rows = startupRows(listOf(StartupSpan(1, "database", 100, 50), StartupSpan(2, "graph", 150, 150), StartupSpan(3, "frame", 300, 0)))
        assertEquals(listOf("0", "50", "200"), rows.map { it.offsetMillis })
        assertEquals(listOf("50", "150", "0"), rows.map { it.durationMillis })
        assertEquals(listOf(0f, 0.25f, 1f), rows.map { it.startFraction })
        assertEquals(listOf(0.25f, 1f, 1f), rows.map { it.endFraction })
        assertTrue(rows.last().isLast)
        assertEquals(0f, startupRows(listOf(StartupSpan(1, "only", 0, 0))).single().endFraction)
        assertTrue(startupRows(emptyList()).isEmpty())
    }
}
