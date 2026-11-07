package io.github.mothlight.compose

import io.github.mothlight.*
import kotlin.test.*

class LogPresentationTest {
    @Test fun combinesMessageSearchSeverityAndExactTagWithoutChangingSource() {
        val entries = listOf(
            log("1", LogLevel.ERROR, "Cache", "Write failed\nretry later"),
            log("2", LogLevel.WARN, "Cache", "Read failed"),
            log("3", LogLevel.ERROR, "Network", "Request failed"),
        )
        assertEquals(listOf("3", "2", "1"), filteredLogs(entries, "FAILED", LogLevelFilter.All, LogTagFilter.All).map { it.id })
        assertEquals(listOf("1"), filteredLogs(entries, "failed", LogLevelFilter.Level(LogLevel.ERROR), LogTagFilter.Tag("Cache")).map { it.id })
        assertEquals(listOf("3"), filteredLogs(entries, "network", LogLevelFilter.All, LogTagFilter.All).map { it.id })
        assertEquals(listOf("1", "2", "3"), entries.map { it.id })
        assertEquals("[1000] ERROR Cache\nWrite failed\nretry later", exportLogs(listOf(entries.first())))
    }
    @Test fun pendingCountIncludesOnlyUnseenRetainedMatchingRecords() {
        val entries = listOf(log("2", LogLevel.INFO), log("3", LogLevel.ERROR), log("4", LogLevel.ERROR))
        val errors = filteredLogs(entries, "", LogLevelFilter.Level(LogLevel.ERROR), LogTagFilter.All)
        assertEquals(2, pendingLogs(errors, setOf("1", "2")))
        assertEquals(0, pendingLogs(emptyList(), setOf("1")))
    }
    @Test fun relativeTimesDoNotAssumeEpochAndHandleNegativeAndExtremeClocks() {
        assertEquals("t+00:06.200", relativeLogTime(7200, 1000))
        assertEquals("t-00:00.001", relativeLogTime(999, 1000))
        assertEquals("t+01:00.000", relativeLogTime(60000, 0))
        assertEquals("t+307445734561825:51.615", relativeLogTime(Long.MAX_VALUE, Long.MIN_VALUE))
    }
    private fun log(id: String, level: LogLevel, tag: String = "Tag", message: String = "message") = LogEntry(id, 1000, level, tag, message)
}
