package io.github.mothlight.compose

import io.github.mothlight.memory.MemorySnapshot

internal enum class MemoryPressure { NEUTRAL, ELEVATED, HIGH }
internal enum class MemoryMetric { HEAP, HEAP_USAGE, NATIVE_HEAP, JVM_NON_HEAP, RESIDENT, FOOTPRINT }
internal enum class MemoryUnit { MIB, GIB, PERCENT }
internal data class MemoryRow(val metric: MemoryMetric, val value: String, val unit: MemoryUnit)
internal data class MemoryPresentation(val rows: List<MemoryRow>, val pressure: MemoryPressure)
internal fun memoryPresentation(snapshot: MemorySnapshot): MemoryPresentation = MemoryPresentation(emptyList(), MemoryPressure.NEUTRAL)
