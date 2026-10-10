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
        val process = ProcessBuilder(executable.absolutePath, ProcessHandle.current().pid().toString(), "GC.heap_dump", heapDumpArgument(destination))
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


private fun heapDumpArgument(destination: File): String {
    val path = destination.absolutePath
    // jcmd joins argv and reparses it as a diagnostic command (not a shell command).
    // Quote that second grammar; reject line breaks and paths it cannot represent faithfully.
    if (path.any { it == '\n' || it == '\r' || it == '\u0000' }) throw UnsupportedOperationException("Unsupported heap dump path")
    val quote = when {
        '"' !in path -> '"'
        '\'' !in path -> '\''
        else -> throw UnsupportedOperationException("Unsupported heap dump path")
    }
    return "$quote$path$quote"
}
