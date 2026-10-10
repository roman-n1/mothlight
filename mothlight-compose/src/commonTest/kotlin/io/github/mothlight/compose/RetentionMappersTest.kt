package io.github.mothlight.compose

import io.github.mothlight.retention.*
import kotlin.test.*

class RetentionMappersTest {
    private val snapshot = RetentionSnapshot(WatcherMode.RUNNING, RetentionSupport.KOTLIN_NATIVE, listOf(
        RetentionEntry(1, "Old", "screen closed", RetentionState.COLLECTED, 0, 10000),
        RetentionEntry(2, "New", "request complete", RetentionState.WATCHING, 3000, 1000),
        RetentionEntry(3, "Screen", "listener removed", RetentionState.RETAINED, 0, 5999),
        RetentionEntry(4, "Screen", "scope cancelled", RetentionState.RETAINED, 0, 6000),
    ), retainedCount = 2)
    @Test fun retainedFirstThenAgeAndFiltersMatchLabelsAndReasons() {
        assertEquals(listOf(4L, 3L, 2L, 1L), retentionRows(snapshot, "", RetentionFilter.ALL).map { it.id })
        assertEquals(listOf("6.0", "5.9"), retentionRows(snapshot, "SCREEN", RetentionFilter.RETAINED).map { it.seconds })
        assertEquals(listOf(3L), retentionRows(snapshot, "LISTENER", RetentionFilter.ALL).map { it.id })
        assertEquals(listOf(1L), retentionRows(snapshot, "", RetentionFilter.COLLECTED).map { it.id })
        assertTrue(retentionRows(snapshot, "missing", RetentionFilter.ALL).isEmpty())
    }
    @Test fun unsupportedHasNoInventedRowsAndDuplicateLabelsKeepIds() {
        assertTrue(retentionRows(RetentionSnapshot(WatcherMode.UNSUPPORTED, RetentionSupport.UNSUPPORTED), "", RetentionFilter.ALL).isEmpty())
        assertEquals(setOf(3L, 4L), retentionRows(snapshot, "Screen", RetentionFilter.RETAINED).map { it.id }.toSet())
    }
    @Test fun overlayCanSelectToolAndMissingToolFallsBackWithoutSharingState() {
        val state = MothlightPanelState()
        val other = MothlightPanelState()
        state.selectTool("retention")
        assertEquals(ToolSelection.Named("retention"), state.selectedTool.value)
        assertEquals(ToolSelection.Default, other.selectedTool.value)
        assertEquals("retention", resolveToolId(listOf("http", "retention"), state.selectedTool.value))
        assertEquals("http", resolveToolId(listOf("http"), state.selectedTool.value))
        assertFailsWith<IllegalArgumentException> { state.selectTool(" ") }
    }
}
