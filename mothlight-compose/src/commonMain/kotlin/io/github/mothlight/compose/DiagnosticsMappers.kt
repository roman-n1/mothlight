package io.github.mothlight.compose

import io.github.mothlight.*

internal data class StartupRow(val id: Long, val stage: String, val offsetMillis: String, val durationMillis: String, val fraction: Float)
internal data class LifecycleRow(val kind: String, val created: String, val destroyed: String, val balance: String)
internal fun startupRows(spans: List<StartupSpan>): List<StartupRow> {
    if (spans.isEmpty()) return emptyList()
    val origin = spans.first().startedAtMillis
    val total = spans.last().startedAtMillis.toDouble() - origin.toDouble()
    return spans.map { span ->
        val fraction = if (total > 0) (span.durationMillis / total).toFloat().coerceIn(0f, 1f) else 0f
        StartupRow(span.id, span.stage, (span.startedAtMillis - origin).toString(), span.durationMillis.toString(), fraction)
    }
}
internal fun lifecycleRows(snapshot: LifecycleSnapshot, query: String): List<LifecycleRow> = snapshot.counters
    .filter { it.kind.contains(query, ignoreCase = true) }.sortedBy { it.kind.lowercase() }
    .map { LifecycleRow(it.kind, it.created.toString(), it.destroyed.toString(), (it.created - it.destroyed).toString()) }
internal fun lifecycleEvents(snapshot: LifecycleSnapshot, query: String): List<LifecycleEntry> = snapshot.events
    .filter { it.kind.contains(query, ignoreCase = true) }.asReversed()
internal fun navigationRows(entries: List<NavigationEntry>, query: String): List<NavigationEntry> = entries
    .filter { it.destination.contains(query, ignoreCase = true) }.asReversed()
internal fun invariantRows(snapshot: InvariantSnapshot, query: String): List<InvariantStat> = snapshot.counters
    .filter { it.rule.contains(query, ignoreCase = true) || screenMatches(it.lastScreen, query) }
    .sortedWith(compareByDescending<InvariantStat> { it.highestSeverity }.thenByDescending { it.count }.thenBy { it.rule })

private fun screenMatches(screen: ScreenContext, query: String) = when (screen) {
    ScreenContext.Unspecified -> false
    is ScreenContext.Named -> screen.value.contains(query, ignoreCase = true)
}
