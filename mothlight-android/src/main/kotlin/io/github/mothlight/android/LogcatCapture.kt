package io.github.mothlight.android

import android.os.Build
import android.os.Process
import io.github.mothlight.LogEntry
import io.github.mothlight.LogLevel
import io.github.mothlight.Mothlight
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Own-process Logcat, active only while recording is enabled. No READ_LOGS permission needed.
 * The host supplies a sanitizer: free-text logs are not covered by HTTP JSON redaction.
 * Cancel the owner scope or call [LogcatCapture.close] to terminate the subprocess.
 */
fun captureLogcat(
    scope: CoroutineScope,
    mothlight: Mothlight,
    tags: Set<String> = emptySet(),
    sanitize: (String) -> String,
): LogcatCapture {
    val pid = Process.myPid()
    val command = buildList {
        addAll(listOf("logcat", "-b", "main", "-v", "epoch", "-T", "1"))
        if (Build.VERSION.SDK_INT >= 24) add("--pid=$pid")
        add("*:V")
    }
    return LogcatCapture(scope, mothlight, LogcatSource {
        ProcessLogcatStream(ProcessBuilder(command).redirectErrorStream(true).start())
    }, pid, tags.toSet(), sanitize, Dispatchers.IO)
}

/** UNAVAILABLE includes denied access, unsupported output and an unexpectedly ended reader.
 * Toggle recording off/on to retry; failures never start a restart loop.
 */
enum class LogcatStatus { STOPPED, READING, UNAVAILABLE }

class LogcatCapture internal constructor(
    scope: CoroutineScope,
    mothlight: Mothlight,
    source: LogcatSource,
    pid: Int,
    tags: Set<String>,
    sanitize: (String) -> String,
    dispatcher: CoroutineDispatcher,
) {
    private val mutableStatus = MutableStateFlow(LogcatStatus.STOPPED)
    val status: StateFlow<LogcatStatus> = mutableStatus.asStateFlow()
    private val parser = LogcatParser(pid, tags, sanitize)
    private val job = scope.launch(dispatcher) {
        try {
            mothlight.enabled.collectLatest { enabled ->
                mutableStatus.value = LogcatStatus.STOPPED
                if (!enabled) return@collectLatest
                try {
                    val stream = source.open()
                    // WHY: close in the await's finally, before coroutineScope waits for its
                    // child. A blocking read must be unblocked by destroying the process.
                    coroutineScope {
                        try {
                            mutableStatus.value = LogcatStatus.READING
                            val reader = async(dispatcher) {
                                stream.read { line -> parser.accept(line, mothlight.logs::record) }
                            }
                            reader.await()
                        } finally { stream.close() }
                    }
                    mutableStatus.value = LogcatStatus.UNAVAILABLE
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableStatus.value = LogcatStatus.UNAVAILABLE
                }
            }
        } finally { mutableStatus.value = LogcatStatus.STOPPED }
    }
    fun close() { job.cancel() }
}

internal fun interface LogcatSource { fun open(): LogcatStream }
internal interface LogcatStream { suspend fun read(emit: (String) -> Unit); fun close() }
private class ProcessLogcatStream(private val process: java.lang.Process) : LogcatStream {
    override suspend fun read(emit: (String) -> Unit) {
        process.inputStream.bufferedReader().use { reader ->
            while (currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                emit(line)
            }
        }
    }
    override fun close() { process.destroy() }
}

internal class LogcatParser(private val pid: Int, private val tags: Set<String>, private val sanitize: (String) -> String) {
    private val session = UUID.randomUUID().toString()
    private var sequence = 0L
    private val pattern = Regex("""^\s*(\d+)\.(\d+)\s+(\d+)\s+\d+\s+([VDIWEF])\s+([^:]+): ?(.*)$""")
    fun accept(line: String, emit: (LogEntry) -> Unit) {
        if (line.length > 20000) return
        val match = pattern.matchEntire(line) ?: return
        val (seconds, fraction, processId, priority, rawTag, rawMessage) = match.destructured
        if (processId.toIntOrNull() != pid) return
        val tag = rawTag.trim()
        if (tags.isNotEmpty() && tag !in tags) return
        val epoch = seconds.toLongOrNull() ?: return
        if (epoch > (Long.MAX_VALUE - 999) / 1000) return
        val millis = fraction.take(3).padEnd(3, '0').toIntOrNull() ?: return
        val level = when (priority) {
            "V", "D" -> LogLevel.DEBUG
            "W" -> LogLevel.WARN
            "E", "F" -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
        val message = try { sanitize(rawMessage.take(4000)).take(4000) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return }
        emit(LogEntry("logcat-$session-${++sequence}", epoch * 1000 + millis, level, tag, message))
    }
}
