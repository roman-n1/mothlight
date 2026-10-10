package io.github.mothlight.retention

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RetentionAnalysisTest {
    @Test fun defaultAnalyzerReportsUnsupportedInsteadOfMissingImplementation() = runTest {
        assertEquals(RetentionAnalysisResult.Unsupported,
            UnsupportedRetentionAnalyzer.analyze(RetentionAnalysisRequest("s", 0, emptyList())))
    }

    @Test fun requestsUseSessionAndOnlyCurrentlyRetainedEntries() = runTest {
        val backend = FakeBackend()
        var now = 0L
        val watcher = RetentionWatcher(backgroundScope, backend, graceMillis = 5, dispatcher = StandardTestDispatcher(testScheduler), nowMillis = { now })
        val other = RetentionWatcher(backgroundScope, FakeBackend())
        watcher.setEnabled(true)
        watcher.expectReleased(Any(), "released", "done")
        val retained = assertIs<WatchResult.Accepted>(watcher.expectReleased(Any(), "retained", "done")).id
        assertTrue(watcher.analysisRequest().entries.isEmpty())
        backend.handles.first().alive = false
        now = 5; watcher.checkNow()
        val request = watcher.analysisRequest()
        assertEquals(listOf(retained), request.entries.map { it.id })
        assertNotEquals(request.sessionId, other.analysisRequest().sessionId)
        assertTrue(watcher.isCurrent(request))
        watcher.clear()
        assertFalse(watcher.isCurrent(request))
        watcher.close(); other.close()
    }
    @Test fun analysisIsSingleFlightAndClearDiscardsLateResult() = runTest {
        val watcher = retainedWatcher()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val analyzer = RetentionAnalyzer { request ->
            calls++; release.await(); RetentionAnalysisResult.Report(request.entries.map { RetentionFinding.NotInDump(it.id) })
        }
        val controller = RetentionAnalysisController(watcher, analyzer)
        val task = launch { controller.analyze() }
        runCurrent()
        assertIs<RetentionAnalysisState.Running>(controller.snapshot)
        controller.analyze()
        assertEquals(1, calls)
        watcher.clear()
        assertEquals(RetentionAnalysisState.Idle, controller.snapshot)
        release.complete(Unit); task.join()
        assertEquals(RetentionAnalysisState.Idle, controller.snapshot)
        watcher.close()
    }
    @Test fun cancellationPropagatesAndNextAnalysisCanRun() = runTest {
        val watcher = retainedWatcher()
        var block = true
        val controller = RetentionAnalysisController(watcher, RetentionAnalyzer {
            if (block) awaitCancellation()
            RetentionAnalysisResult.Unsupported
        })
        val task = launch { controller.analyze() }; runCurrent()
        task.cancelAndJoin()
        assertEquals(RetentionAnalysisState.Idle, controller.snapshot)
        block = false
        controller.analyze()
        assertEquals(RetentionAnalysisResult.Unsupported, assertIs<RetentionAnalysisState.Finished>(controller.snapshot).result)
        watcher.close()
    }
    @Test fun failuresAreTypedAndEmptyRequestNeverInvokesBackend() = runTest {
        val watcher = retainedWatcher()
        var calls = 0
        val controller = RetentionAnalysisController(watcher, RetentionAnalyzer { calls++; error("private path or data") })
        controller.analyze()
        assertEquals(RetentionAnalysisResult.Failed(AnalysisFailure.ANALYSIS), assertIs<RetentionAnalysisState.Finished>(controller.snapshot).result)
        watcher.clear(); controller.analyze()
        assertEquals(1, calls)
        assertEquals(RetentionAnalysisState.Idle, controller.snapshot)
        watcher.close()
    }
    private suspend fun TestScope.retainedWatcher(): RetentionWatcher {
        val watcher = RetentionWatcher(backgroundScope, FakeBackend(), graceMillis = 0, dispatcher = StandardTestDispatcher(testScheduler))
        watcher.setEnabled(true)
        watcher.expectReleased(Any(), "held", "disposed")
        watcher.checkNow()
        return watcher
    }
}
