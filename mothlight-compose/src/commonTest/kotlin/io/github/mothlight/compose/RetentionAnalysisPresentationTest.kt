package io.github.mothlight.compose

import io.github.mothlight.retention.*
import kotlin.test.*

class RetentionAnalysisPresentationTest {
    @Test fun findingsUseWatchedIdsInsteadOfTheirPosition() {
        val request = RetentionAnalysisRequest("s", 0, listOf(
            RetentionEntry(4, "first", "done", RetentionState.RETAINED, 0, 0),
            RetentionEntry(8, "second", "done", RetentionState.RETAINED, 0, 0),
        ))
        val rows = analysisRows(request, RetentionAnalysisResult.Report(listOf(
            RetentionFinding.NoStrongPath(8), RetentionFinding.StrongPath(4, RetainingPath("root", emptyList(), "Target", "sig")), RetentionFinding.NotInDump(99),
        )))
        assertEquals(listOf(AnalysisRow(8, "second", FindingStatus.NO_STRONG_PATH), AnalysisRow(4, "first", FindingStatus.STRONG_PATH)), rows)
        val running = RetentionSnapshot(WatcherMode.RUNNING, RetentionSupport.MANAGED_HEAP, retainedCount = 1)
        assertTrue(canAnalyze(running, RetentionAnalysisState.Idle))
        assertFalse(canAnalyze(running, RetentionAnalysisState.Running(request)))
        assertFalse(canAnalyze(running.copy(retainedCount = 0), RetentionAnalysisState.Idle))
        assertFalse(canAnalyze(running.copy(mode = WatcherMode.DISABLED), RetentionAnalysisState.Idle))
    }
}
