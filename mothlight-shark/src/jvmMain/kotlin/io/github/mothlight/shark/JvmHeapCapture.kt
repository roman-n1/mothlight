package io.github.mothlight.shark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.util.concurrent.TimeUnit

/** Requires a local JDK with jcmd and HotSpot attach enabled. Pass a private writable cache directory. */
fun jvmRetentionAnalyzer(directory: File): SharkRetentionAnalyzer = SharkRetentionAnalyzer(directory, HeapDumpCapture { destination ->
    runInterruptible(Dispatchers.IO) {
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        val executable = File(System.getProperty("java.home"), "bin/jcmd$suffix")
        if (!executable.isFile) throw UnsupportedOperationException("JDK heap capture unavailable")
        val process = ProcessBuilder(executable.absolutePath, ProcessHandle.current().pid().toString(), "GC.heap_dump", destination.absolutePath)
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        try {
            check(process.waitFor(60, TimeUnit.SECONDS)) { "Heap capture timed out" }
            check(process.exitValue() == 0 && destination.isFile) { "Heap capture failed" }
        } finally {
            // jcmd is only the client. If cancelled, wait for its command to finish before deleting the dump;
            // killing the client cannot safely cancel an in-progress VM heap write.
            var interrupted = Thread.interrupted()
            while (process.isAlive) {
                try { process.waitFor() } catch (_: InterruptedException) { interrupted = true }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
})
