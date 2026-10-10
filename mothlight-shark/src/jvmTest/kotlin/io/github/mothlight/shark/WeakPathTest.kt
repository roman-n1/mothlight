package io.github.mothlight.shark

import io.github.mothlight.retention.*
import kotlinx.coroutines.runBlocking
import shark.*
import shark.HprofRecord.*
import shark.HprofRecord.HeapDumpRecord.*
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.*
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord.*
import shark.ValueHolder.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class WeakPathTest {
    @Test fun weakOnlyPathIsNotReportedAsStrongRetention() = runBlocking {
        val directory = Files.createTempDirectory("mothlight-weak-path").toFile()
        val request = RetentionAnalysisRequest("session", 0, listOf(RetentionEntry(7, "target", "done", RetentionState.RETAINED, 0, 0)))
        try {
            val weak = SharkRetentionAnalyzer(directory, HeapDumpCapture { writeFixture(it, strongRoot = false) })
            val weakReport = assertIs<RetentionAnalysisResult.Report>(weak.analyze(request))
            assertEquals(listOf(RetentionFinding.NoStrongPath(7)), weakReport.findings)
            val strong = SharkRetentionAnalyzer(directory, HeapDumpCapture { writeFixture(it, strongRoot = true) })
            val finding = assertIs<RetentionAnalysisResult.Report>(strong.analyze(request)).findings.single()
            assertEquals(FindingStatus.STRONG_PATH, finding.status)
            assertEquals(listOf(RetainingReference("fixture.Root", "held", HeapReferenceKind.STATIC_FIELD)), assertIs<RetentionFinding.StrongPath>(finding).path.references)
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }
}

/** The same target is present in both graphs. Only the strong root edge changes. No real-GC timing. */
private fun writeFixture(file: File, strongRoot: Boolean) {
    HprofWriter.openWriterFor(file).use { writer ->
        var stringId = 1000L
        fun string(value: String): Long = (++stringId).also { writer.write(StringRecord(it, value)) }
        fun clazz(id: Long, name: String, superId: Long, fields: List<FieldRecord> = emptyList(), statics: List<StaticFieldRecord> = emptyList()) {
            writer.write(LoadClassRecord(id.toInt(), id, 0, string(name)))
            writer.write(ClassDumpRecord(id, 0, superId, 0, 0, 0, 32, statics, fields))
        }
        clazz(1, "java.lang.Object", 0)
        clazz(2, "java.lang.String", 1, listOf(FieldRecord(string("value"), 2)))
        clazz(3, "java.lang.ref.Reference", 1, listOf(FieldRecord(string("referent"), 2)))
        clazz(4, "io.github.mothlight.retention.HeapWatchReference", 3, listOf(FieldRecord(string("sessionId"), 2), FieldRecord(string("watchId"), 11)))
        clazz(5, "fixture.Target", 1, listOf(FieldRecord(string("payload"), 10)))
        val fields = mutableListOf(StaticFieldRecord(string("watch"), 2, ReferenceHolder(200)))
        if (strongRoot) fields += StaticFieldRecord(string("held"), 2, ReferenceHolder(201))
        clazz(6, "fixture.Root", 1, statics = fields)
        writer.write(InstanceDumpRecord(200, 0, 4, writer.valuesToBytes(listOf(ReferenceHolder(300), LongHolder(7), ReferenceHolder(201)))))
        writer.write(InstanceDumpRecord(201, 0, 5, writer.valuesToBytes(listOf(IntHolder(42)))))
        writer.write(InstanceDumpRecord(300, 0, 2, writer.valuesToBytes(listOf(ReferenceHolder(301)))))
        writer.write(PrimitiveArrayDumpRecord.CharArrayDump(301, 0, "session".toCharArray()))
        writer.write(GcRootRecord(GcRoot.StickyClass(6)))
    }
}
