package io.github.mothlight.compose

import io.github.mothlight.memory.*
import kotlin.math.roundToInt

internal enum class MemoryPressure { NEUTRAL, ELEVATED, HIGH }
internal enum class MemoryMetric { HEAP, HEAP_USAGE, NATIVE_HEAP, JVM_NON_HEAP, RESIDENT, FOOTPRINT }
internal enum class MemoryUnit { MIB, GIB, PERCENT }
internal data class MemoryRow(val metric: MemoryMetric, val value: String, val unit: MemoryUnit)
internal data class MemoryPresentation(val rows: List<MemoryRow>, val pressure: MemoryPressure)
private const val MIB = 1024L * 1024
private const val GIB = 1024L * MIB

internal fun memoryPresentation(snapshot: MemorySnapshot): MemoryPresentation {
    val used = snapshot.heapUsedBytes?.takeIf { it >= 0 }
    val limit = snapshot.heapLimitBytes?.takeIf { it > 0 }
    val fraction = if (used != null && limit != null) used.toDouble() / limit else null
    val rows = buildList {
        if (used != null) {
            val unit = sizeUnit(limit ?: used)
            val pair = if (limit == null) sizeValue(used, unit) else "${sizeValue(used, unit)}/${sizeValue(limit, unit)}"
            add(MemoryRow(MemoryMetric.HEAP, pair, unit))
            fraction?.let { add(MemoryRow(MemoryMetric.HEAP_USAGE, (it * 100).roundToInt().toString(), MemoryUnit.PERCENT)) }
        }
        snapshot.auxiliaryBytes?.takeIf { it >= 0 }?.let {
            val metric = when (snapshot.auxiliaryKind) {
                AuxiliaryMemoryKind.NATIVE_HEAP -> MemoryMetric.NATIVE_HEAP
                AuxiliaryMemoryKind.JVM_NON_HEAP -> MemoryMetric.JVM_NON_HEAP
            }
            add(MemoryRow(metric, sizeValue(it, sizeUnit(it)), sizeUnit(it)))
        }
        snapshot.processBytes?.takeIf { it >= 0 }?.let {
            val metric = when (snapshot.processKind) {
                ProcessMemoryKind.RESIDENT -> MemoryMetric.RESIDENT
                ProcessMemoryKind.PHYSICAL_FOOTPRINT -> MemoryMetric.FOOTPRINT
            }
            add(MemoryRow(metric, sizeValue(it, sizeUnit(it)), sizeUnit(it)))
        }
    }
    val pressure = when {
        fraction == null -> MemoryPressure.NEUTRAL
        fraction > 0.8 -> MemoryPressure.HIGH
        fraction > 0.6 -> MemoryPressure.ELEVATED
        else -> MemoryPressure.NEUTRAL
    }
    return MemoryPresentation(rows, pressure)
}

private fun sizeUnit(bytes: Long) = if (bytes >= GIB) MemoryUnit.GIB else MemoryUnit.MIB
private fun sizeValue(bytes: Long, unit: MemoryUnit): String = when (unit) {
    MemoryUnit.GIB -> "${bytes / GIB}.${(bytes % GIB) * 10 / GIB}"
    MemoryUnit.MIB -> (bytes / MIB).toString()
    MemoryUnit.PERCENT -> error("Percentage is not a byte unit")
}
