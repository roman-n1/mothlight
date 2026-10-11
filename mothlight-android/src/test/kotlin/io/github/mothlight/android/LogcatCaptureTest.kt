package io.github.mothlight.android

import io.github.mothlight.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LogcatCaptureTest {
    @Test fun parsesOnlyOwnProcessAndAllowedTagsWithUniqueIds() {
        val entries = mutableListOf<LogEntry>()
        val parser = LogcatParser(42, setOf("Weather")) { it.replace("secret", "[redacted]") }
        listOf("--------- beginning of main", "broken", "1720000000.123 43 8 I Weather: foreign", "1720000000.123 42 8 I Other: ignored", "1720000000.123 42 8 W Weather: secret: retry", "1720000000.123 42 8 W Weather: secret: retry").forEach { parser.accept(it, entries::add) }
        assertEquals(2, entries.size)
        assertEquals(1720000000123L, entries.first().timestampMillis)
        assertEquals(LogLevel.WARN, entries.first().level)
        assertEquals("[redacted]: retry", entries.first().message)
        assertEquals(2, entries.map { it.id }.toSet().size)
    }
    @Test fun parserBoundsMessagesAndDropsSanitizerFailures() {
        val entries = mutableListOf<LogEntry>()
        LogcatParser(42, emptySet()) { error("bad sanitizer") }.accept("1720000000.1 42 8 I Weather: data", entries::add)
        assertTrue(entries.isEmpty())
        LogcatParser(42, emptySet()) { it.repeat(20) }.accept("1720000000.1 42 8 V Weather: " + "a".repeat(8000), entries::add)
        assertEquals(1, entries.size)
        assertEquals(LogLevel.DEBUG, entries.single().level)
        assertTrue(entries.single().message.length <= 4000)
    }
    @Test fun disablingAndClosingCaptureReleaseStreamAndRestartOnce() = runTest {
        val mothlight = Mothlight()
        val opened = mutableListOf<FakeStream>()
        val capture = LogcatCapture(backgroundScope, mothlight, LogcatSource { FakeStream().also(opened::add) }, 42, emptySet(), { it }, StandardTestDispatcher(testScheduler))
        runCurrent()
        assertTrue(opened.isEmpty())
        mothlight.setEnabled(true); runCurrent()
        assertEquals(1, opened.size)
        assertEquals(LogcatStatus.READING, capture.status.value)
        opened.single().lines.send("1720000000.1 42 8 E Weather: failed"); runCurrent()
        assertEquals("failed", mothlight.logs.items.value.single().message)
        mothlight.setEnabled(false); runCurrent()
        assertEquals(1, opened.first().closes)
        assertEquals(LogcatStatus.STOPPED, capture.status.value)
        mothlight.setEnabled(true); runCurrent()
        assertEquals(2, opened.size)
        capture.close(); runCurrent(); capture.close()
        assertEquals(1, opened.last().closes)
        assertEquals(LogcatStatus.STOPPED, capture.status.value)
    }
    @Test fun unavailableProcessIsReportedWithoutCancellingHost() = runTest {
        val mothlight = Mothlight().apply { setEnabled(true) }
        val capture = LogcatCapture(backgroundScope, mothlight, LogcatSource { throw java.io.IOException("denied") }, 42, emptySet(), { it }, StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(LogcatStatus.UNAVAILABLE, capture.status.value)
        assertTrue(backgroundScope.isActive)
    }
    @Test fun parentCancellationClosesReader() = runTest {
        val owner = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        val stream = FakeStream()
        val capture = LogcatCapture(owner, Mothlight().apply { setEnabled(true) }, LogcatSource { stream }, 42, emptySet(), { it }, StandardTestDispatcher(testScheduler))
        runCurrent(); owner.cancel(); runCurrent()
        assertEquals(1, stream.closes)
        assertEquals(LogcatStatus.STOPPED, capture.status.value)
    }
    @Test fun cancellationClosesBeforeWaitingForBlockedReader() = runTest {
        val released = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val stream = object : LogcatStream {
            override suspend fun read(emit: (String) -> Unit) {
                entered.complete(Unit)
                withContext(NonCancellable) { released.await() }
            }
            override fun close() { released.complete(Unit) }
        }
        val capture = LogcatCapture(backgroundScope, Mothlight().apply { setEnabled(true) }, LogcatSource { stream }, 42, emptySet(), { it }, StandardTestDispatcher(testScheduler))
        runCurrent(); assertTrue(entered.isCompleted)
        capture.close(); runCurrent()
        assertTrue(released.isCompleted)
        assertEquals(LogcatStatus.STOPPED, capture.status.value)
    }
    private class FakeStream : LogcatStream {
        val lines = Channel<String>(Channel.UNLIMITED)
        var closes = 0
        override suspend fun read(emit: (String) -> Unit) { for (line in lines) emit(line) }
        override fun close() { closes++; lines.close() }
    }
}
