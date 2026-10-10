package io.github.mothlight.compose

import io.github.mothlight.*

internal data class StartupRow(val id: Long, val stage: String, val offsetMillis: String, val durationMillis: String, val fraction: Float)
internal data class LifecycleRow(val kind: String, val created: String, val destroyed: String, val balance: String)
internal fun startupRows(spans: List<StartupSpan>): List<StartupRow> = emptyList()
internal fun lifecycleRows(snapshot: LifecycleSnapshot, query: String): List<LifecycleRow> = emptyList()
internal fun lifecycleEvents(snapshot: LifecycleSnapshot, query: String): List<LifecycleEntry> = emptyList()
internal fun navigationRows(entries: List<NavigationEntry>, query: String): List<NavigationEntry> = emptyList()
internal fun invariantRows(snapshot: InvariantSnapshot, query: String): List<InvariantStat> = emptyList()
