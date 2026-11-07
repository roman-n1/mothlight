package io.github.mothlight.compose

import io.github.mothlight.*

internal sealed interface LogLevelFilter {
    data object All : LogLevelFilter
    data class Level(val level: LogLevel) : LogLevelFilter
}
internal sealed interface LogTagFilter {
    data object All : LogTagFilter
    data class Tag(val value: String) : LogTagFilter
}
internal sealed interface LogSelection {
    data object Collapsed : LogSelection
    data class Entry(val id: String) : LogSelection
}
internal sealed interface LogTimeOrigin {
    data object Unset : LogTimeOrigin
    data class At(val millis: Long) : LogTimeOrigin
}

internal fun filteredLogs(entries: List<LogEntry>, query: String, level: LogLevelFilter, tag: LogTagFilter): List<LogEntry> =
    entries.asReversed().filter { entry ->
        (entry.message.contains(query, ignoreCase = true) || entry.tag.contains(query, ignoreCase = true)) &&
            (level == LogLevelFilter.All || level == LogLevelFilter.Level(entry.level)) &&
            (tag == LogTagFilter.All || tag == LogTagFilter.Tag(entry.tag))
    }

internal fun pendingLogs(visible: List<LogEntry>, seen: Set<String>) = visible.count { it.id !in seen }
internal fun exportLogs(entries: List<LogEntry>) = entries.joinToString("\n\n") { "[${it.timestampMillis}] ${it.level} ${it.tag}\n${it.message}" }

/** The host supplies the clock; do not interpret these timestamps as epoch milliseconds. */
internal fun relativeLogTime(timestamp: Long, origin: Long): String {
    val forward = timestamp >= origin
    val distance = if (forward) timestamp.toULong() - origin.toULong() else origin.toULong() - timestamp.toULong()
    val minutes = (distance / 60000uL).toString().padStart(2, '0')
    val seconds = (distance / 1000uL % 60uL).toString().padStart(2, '0')
    val millis = (distance % 1000uL).toString().padStart(3, '0')
    return "t" + (if (forward) "+" else "-") + "$minutes:$seconds.$millis"
}
