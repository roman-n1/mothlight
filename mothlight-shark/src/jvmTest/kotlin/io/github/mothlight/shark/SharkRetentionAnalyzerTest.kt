package io.github.mothlight.shark

import io.github.mothlight.retention.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class SharkRetentionAnalyzerTest {
    @Test fun realDumpFindsStrongRootAndReleasedControlWithoutMixingSessions() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val watcher = RetentionWatcher(scope, graceMillis = 0)
        val other = RetentionWatcher(scope, graceMillis = 0)
        val directory = Files.createTempDirectory("mothlight-analysis-test").toFile()
        try {
            watcher.setEnabled(true); other.setEnabled(true)
            val retained = registerRoot(watcher)
            val duplicate = (watcher.expectReleased(HeapRoots.held!!, "duplicate", "disposed") as WatchResult.Accepted).id
            val released = registerReleasedControl(watcher)
            registerDecoy(other)
            watcher.checkNow()
            val request = watcher.analysisRequest()
            assertEquals(setOf(retained, duplicate, released), request.entries.map { it.id }.toSet())
            HeapRoots.released = null
            val report = assertIs<RetentionAnalysisResult.Report>(jvmRetentionAnalyzer(directory).analyze(request))
            val finding = report.findings.single { it.watchId == retained }
            assertEquals(FindingStatus.STRONG_PATH, finding.status)
            val path = assertNotNull(finding.path)
            assertTrue(path.references.any { it.ownerClass.endsWith("HeapRoots") && it.referenceName == "held" }, path.toString())
            assertFalse(path.references.any { it.referenceName == "referent" })
            assertTrue(path.targetClass.endsWith("FixtureTarget"))
            assertEquals(path, report.findings.single { it.watchId == duplicate }.path)
            assertEquals(FindingStatus.NOT_IN_DUMP, report.findings.single { it.watchId == released }.status)
            assertTrue(directory.listFiles()!!.isEmpty(), "Raw heap dump must be deleted after success")
            HeapRoots.held = null
            val second = assertIs<RetentionAnalysisResult.Report>(jvmRetentionAnalyzer(directory).analyze(request))
            assertTrue(second.findings.all { it.status == FindingStatus.NOT_IN_DUMP })
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { HeapRoots.held = null; HeapRoots.decoy = null; HeapRoots.released = null; watcher.close(); other.close(); scope.cancel(); directory.deleteRecursively() }
    }
    @Test fun corruptOversizedAndCancelledCapturesDeleteRawData() = runBlocking {
        val directory = Files.createTempDirectory("mothlight-failed-analysis").toFile()
        val request = RetentionAnalysisRequest("session", 0, listOf(RetentionEntry(1, "target", "disposed", RetentionState.RETAINED, 0, 0)))
        try {
            val corrupt = SharkRetentionAnalyzer(directory, HeapDumpCapture { it.writeText("private bytes") })
            assertEquals(RetentionAnalysisResult.Failed(AnalysisFailure.ANALYSIS), corrupt.analyze(request))
            assertTrue(directory.listFiles()!!.isEmpty())
            val huge = SharkRetentionAnalyzer(directory, HeapDumpCapture { it.writeBytes(ByteArray(20)) }, maxDumpBytes = 10)
            assertEquals(RetentionAnalysisResult.Failed(AnalysisFailure.DUMP_TOO_LARGE), huge.analyze(request))
            assertTrue(directory.listFiles()!!.isEmpty())
            val cancelled = SharkRetentionAnalyzer(directory, HeapDumpCapture { it.writeText("private bytes"); throw CancellationException("cancel") })
            assertFailsWith<CancellationException> { cancelled.analyze(request) }
            assertTrue(directory.listFiles()!!.isEmpty())
            val failed = SharkRetentionAnalyzer(directory, HeapDumpCapture { it.writeText("private bytes"); error("sensitive") })
            assertEquals(RetentionAnalysisResult.Failed(AnalysisFailure.CAPTURE), failed.analyze(request))
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
private class FixtureTarget(val contents: ByteArray = ByteArray(64))
private object HeapRoots {
    @JvmField var held: FixtureTarget? = null
    @JvmField var decoy: FixtureTarget? = null
    @JvmField var released: FixtureTarget? = null
}
private fun registerRoot(watcher: RetentionWatcher): Long {
    val target = FixtureTarget()
    HeapRoots.held = target
    return (watcher.expectReleased(target, "target", "disposed") as WatchResult.Accepted).id
}
private fun registerReleasedControl(watcher: RetentionWatcher): Long {
    val target = FixtureTarget()
    HeapRoots.released = target
    return (watcher.expectReleased(target, "released control", "disposed") as WatchResult.Accepted).id
}
private fun registerDecoy(watcher: RetentionWatcher) {
    val target = FixtureTarget()
    HeapRoots.decoy = target
    watcher.expectReleased(target, "decoy", "disposed")
}
