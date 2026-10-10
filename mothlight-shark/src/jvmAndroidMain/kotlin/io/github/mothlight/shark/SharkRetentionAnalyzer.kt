package io.github.mothlight.shark

import io.github.mothlight.retention.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import shark.*
import shark.HprofHeapGraph.Companion.openHeapGraph
import java.io.File
import java.util.UUID

/** Write only to destination; return/throw only after all writers have stopped. */
fun interface HeapDumpCapture { suspend fun capture(destination: File) }

/** Optional Android/JVM adapter. No dump leaves this process; raw snapshots are never part of a report. */
class SharkRetentionAnalyzer(
    private val directory: File,
    private val capture: HeapDumpCapture,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxDumpBytes: Long = 256L * 1024 * 1024,
) : RetentionAnalyzer {
    init { require(maxDumpBytes > 0) }
    private val mutex = Mutex()

    override suspend fun analyze(request: RetentionAnalysisRequest): RetentionAnalysisResult {
        if (request.entries.isEmpty()) return RetentionAnalysisResult.Report(emptyList())
        mutex.lock()
        try { return withContext(dispatcher) { captureAndAnalyze(request) } }
        finally { mutex.unlock() }
    }

    private suspend fun captureAndAnalyze(request: RetentionAnalysisRequest): RetentionAnalysisResult {
        val folder = File(directory, "mothlight-" + UUID.randomUUID().toString())
        val dump = File(folder, "heap.hprof")
        var failure = AnalysisFailure.CAPTURE
        var outcome: RetentionAnalysisResult
        var cleanupFailed = false
        try {
            check(folder.mkdirs())
            // Restrict the containing directory before any heap bytes are written (also for desktop hosts).
            check(folder.setReadable(false, false) && folder.setWritable(false, false) && folder.setExecutable(false, false))
            check(folder.setReadable(true, true) && folder.setWritable(true, true) && folder.setExecutable(true, true))
            capture.capture(dump)
            currentCoroutineContext().ensureActive()
            outcome = if (dump.length() > maxDumpBytes) RetentionAnalysisResult.Failed(AnalysisFailure.DUMP_TOO_LARGE) else {
                failure = AnalysisFailure.ANALYSIS
                readDump(dump, request)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: UnsupportedOperationException) {
            outcome = RetentionAnalysisResult.Unsupported
        } catch (_: Exception) {
            outcome = RetentionAnalysisResult.Failed(failure)
        } finally {
            // Capturers must stop writers before returning, including when cancellation is requested.
            cleanupFailed = (dump.exists() && !dump.delete()) || (folder.exists() && !folder.delete())
        }
        return if (cleanupFailed) RetentionAnalysisResult.Failed(AnalysisFailure.CLEANUP) else outcome
    }

    private suspend fun readDump(dump: File, request: RetentionAnalysisRequest): RetentionAnalysisResult {
        val context = currentCoroutineContext()
        return dump.openHeapGraph().use { graph ->
            context.ensureActive()
            val markerClass = graph.findClassByName(MARKER_CLASS)
                ?: return@use RetentionAnalysisResult.Failed(AnalysisFailure.ANALYSIS)
            val requested = request.entries.map { it.id }.toSet()
            val targets = mutableMapOf<Long, Long>()
            markerClass.instances.forEach { marker ->
                context.ensureActive()
                val session = marker[MARKER_CLASS, "sessionId"]?.value?.readAsJavaString()
                val watchId = marker[MARKER_CLASS, "watchId"]?.value?.asLong
                if (session == request.sessionId && watchId in requested) {
                    marker["java.lang.ref.Reference", "referent"]?.value?.asNonNullObjectId?.let { targets[checkNotNull(watchId)] = it }
                }
            }
            val idsByObject = targets.entries.groupBy({ it.value }, { it.key })
            val result = HeapAnalyzer(OnAnalysisProgressListener { context.ensureActive() }).analyze(
                heapDumpFile = dump,
                graph = graph,
                leakingObjectFinder = LeakingObjectFinder { idsByObject.keys },
                referenceMatchers = listOf(
                    IgnoredReferenceMatcher(ReferencePattern.InstanceFieldPattern("java.lang.ref.Reference", "referent")),
                    IgnoredReferenceMatcher(ReferencePattern.InstanceFieldPattern("java.lang.ref.FinalizerReference", "zombie")),
                ),
                computeRetainedHeapSize = false,
                objectInspectors = listOf(ObjectInspector { reporter ->
                    idsByObject[reporter.heapObject.objectId]?.let { ids ->
                        reporter.leakingReasons += "Host reported that the lifetime ended"
                        ids.forEach { reporter.labels += ID_LABEL + it }
                    }
                }),
            )
            // Shark turns Throwable into a failure result; cancellation must still propagate to the caller.
            context.ensureActive()
            if (result !is HeapAnalysisSuccess) return@use RetentionAnalysisResult.Failed(AnalysisFailure.ANALYSIS)
            val paths = mutableMapOf<Long, RetainingPath>()
            result.allLeaks.forEach { leak ->
                leak.leakTraces.forEach { trace ->
                    val path = RetainingPath(
                        trace.gcRootType.name,
                        trace.referencePath.map { ref -> RetainingReference(
                            ref.owningClassName, ref.referenceDisplayName,
                            HeapReferenceKind.valueOf(ref.referenceType.name),
                        ) },
                        trace.leakingObject.className,
                        trace.signature,
                    )
                    trace.leakingObject.labels.forEach { label ->
                        if (label.startsWith(ID_LABEL)) label.removePrefix(ID_LABEL).toLongOrNull()?.let { paths[it] = path }
                    }
                }
            }
            RetentionAnalysisResult.Report(request.entries.map { entry ->
                val path = paths[entry.id]
                when {
                    path != null -> RetentionFinding(entry.id, FindingStatus.STRONG_PATH, path)
                    entry.id in targets -> RetentionFinding(entry.id, FindingStatus.NO_STRONG_PATH)
                    else -> RetentionFinding(entry.id, FindingStatus.NOT_IN_DUMP)
                }
            })
        }
    }
}
private const val MARKER_CLASS = "io.github.mothlight.retention.HeapWatchReference"
private const val ID_LABEL = "mothlight-watch-id:"
