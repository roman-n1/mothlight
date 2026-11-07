package io.github.mothlight.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import io.github.mothlight.*

/** UI metadata only. Pausing follow does not retain an extra copy of the log buffer. */
internal class LogPanelState(val feed: ProbeFeed<LogEntry>) {
    var query by mutableStateOf("")
    var level by mutableStateOf<LogLevelFilter>(LogLevelFilter.All)
    var tag by mutableStateOf<LogTagFilter>(LogTagFilter.All)
    var selection by mutableStateOf<LogSelection>(LogSelection.Collapsed)
    var following by mutableStateOf(true)
    var seenIds by mutableStateOf(emptySet<String>())
        private set
    var origin by mutableStateOf<LogTimeOrigin>(LogTimeOrigin.Unset)
        private set
    val list = LazyListState()

    fun markSeen(entries: List<LogEntry>) { seenIds = entries.map { it.id }.toSet() }
    fun pause(entries: List<LogEntry>) {
        if (following) markSeen(entries)
        following = false
    }
    fun observe(entries: List<LogEntry>) {
        if (origin == LogTimeOrigin.Unset && entries.isNotEmpty()) origin = LogTimeOrigin.At(entries.first().timestampMillis)
        val selected = selection
        if (selected is LogSelection.Entry && entries.none { it.id == selected.id }) selection = LogSelection.Collapsed
        if (entries.isEmpty()) { origin = LogTimeOrigin.Unset; seenIds = emptySet() }
        else if (!following) {
            // Forgotten IDs need not accumulate beyond the current bounded feed.
            seenIds = seenIds.intersect(entries.map { it.id }.toSet())
        }
        if (following) markSeen(entries)
    }
    fun clearView() { selection = LogSelection.Collapsed; origin = LogTimeOrigin.Unset; seenIds = emptySet(); following = true }
}
