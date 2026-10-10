package io.github.mothlight.shark

import io.github.mothlight.retention.*
import kotlinx.coroutines.*
import shark.HprofHeapGraph.Companion.openHeapGraph
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.*

class HeapMarkerTest {
    @Test fun watchedObjectCanBeLocatedInRealHeapDump() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val watcher = RetentionWatcher(scope)
        val directory = Files.createTempDirectory("mothlight-marker-test").toFile()
        try {
            watcher.setEnabled(true)
            val target = MarkerTarget()
            val id = (watcher.expectReleased(target, "marker", "lifetime ended") as WatchResult.Accepted).id
            val dump = File(directory, "heap.hprof")
            dumpCurrentProcess(dump)
            dump.openHeapGraph().use { graph ->
                val markers = graph.findClassByName("io.github.mothlight.retention.HeapWatchReference")?.instances?.toList().orEmpty()
                assertTrue(markers.any { it["io.github.mothlight.retention.HeapWatchReference", "watchId"]?.value?.asLong == id },
                    "A watched object needs a stable ID in the heap dump for cause analysis")
            }
            java.lang.ref.Reference.reachabilityFence(target)
            java.lang.ref.Reference.reachabilityFence(watcher)
        } finally { watcher.close(); scope.cancel(); directory.deleteRecursively() }
    }
}
private class MarkerTarget

internal fun dumpCurrentProcess(file: File) {
    val executable = File(System.getProperty("java.home"), "bin/jcmd").absolutePath
    val process = ProcessBuilder(executable, ProcessHandle.current().pid().toString(), "GC.heap_dump", file.absolutePath)
        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
    try {
        check(process.waitFor(60, TimeUnit.SECONDS)) { "Heap capture timed out" }
        check(process.exitValue() == 0 && file.isFile) { "Heap capture failed" }
    } finally { process.destroyForcibly() }
}
