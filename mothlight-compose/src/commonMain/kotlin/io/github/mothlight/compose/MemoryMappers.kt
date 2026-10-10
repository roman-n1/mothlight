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
    val used = snapshot.heapUsedBytes
    val limit = snapshot.heapLimitBytes
    val rows = buildList {
        if (used is MemoryReading.Bytes) {
            if (limit is MemoryReading.Bytes && limit.value > 0) {
                val unit = sizeUnit(limit.value)
                add(MemoryRow(MemoryMetric.HEAP, "${sizeValue(used.value, unit)}/${sizeValue(limit.value, unit)}", unit))
                add(MemoryRow(MemoryMetric.HEAP_USAGE, (used.value.toDouble() / limit.value * 100).roundToInt().toString(), MemoryUnit.PERCENT))
            } else {
                val unit = sizeUnit(used.value)
                add(MemoryRow(MemoryMetric.HEAP, sizeValue(used.value, unit), unit))
            }
        }
        val auxiliary = snapshot.auxiliaryBytes
        if (auxiliary is MemoryReading.Bytes) {
            val metric = when (snapshot.auxiliaryKind) {
                AuxiliaryMemoryKind.NATIVE_HEAP -> MemoryMetric.NATIVE_HEAP
                AuxiliaryMemoryKind.JVM_NON_HEAP -> MemoryMetric.JVM_NON_HEAP
            }
            add(MemoryRow(metric, sizeValue(auxiliary.value, sizeUnit(auxiliary.value)), sizeUnit(auxiliary.value)))
        }
        val process = snapshot.processBytes
        if (process is MemoryReading.Bytes) {
            val metric = when (snapshot.processKind) {
                ProcessMemoryKind.RESIDENT -> MemoryMetric.RESIDENT
                ProcessMemoryKind.PHYSICAL_FOOTPRINT -> MemoryMetric.FOOTPRINT
            }
            add(MemoryRow(metric, sizeValue(process.value, sizeUnit(process.value)), sizeUnit(process.value)))
        }
    }
    val pressure = if (used is MemoryReading.Bytes && limit is MemoryReading.Bytes && limit.value > 0) {
        val fraction = used.value.toDouble() / limit.value
        when {
            fraction > 0.8 -> MemoryPressure.HIGH
            fraction > 0.6 -> MemoryPressure.ELEVATED
            else -> MemoryPressure.NEUTRAL
        }
    } else MemoryPressure.NEUTRAL
    return MemoryPresentation(rows, pressure)
}

private fun sizeUnit(bytes: Long) = if (bytes >= GIB) MemoryUnit.GIB else MemoryUnit.MIB
private fun sizeValue(bytes: Long, unit: MemoryUnit): String = when (unit) {
    MemoryUnit.GIB -> "${bytes / GIB}.${(bytes % GIB) * 10 / GIB}"
    MemoryUnit.MIB -> (bytes / MIB).toString()
    MemoryUnit.PERCENT -> error("Percentage is not a byte unit")
}
