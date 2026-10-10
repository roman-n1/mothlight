package io.github.mothlight.compose

import io.github.mothlight.memory.*
import kotlin.test.*

private const val MIB = 1024L * 1024
private const val GIB = 1024L * MIB

class MemoryMappersTest {
    @Test fun heapPairUsesCommonUnitAndMetricsStayDistinct() {
        val result = memoryPresentation(MemorySnapshot(20 * MIB, 512 * MIB, 21 * MIB, processBytes = 1434 * MIB))
        assertEquals(listOf(
            MemoryRow(MemoryMetric.HEAP, "20/512", MemoryUnit.MIB),
            MemoryRow(MemoryMetric.HEAP_USAGE, "4", MemoryUnit.PERCENT),
            MemoryRow(MemoryMetric.NATIVE_HEAP, "21", MemoryUnit.MIB),
            MemoryRow(MemoryMetric.RESIDENT, "1.4", MemoryUnit.GIB),
        ), result.rows)
        assertEquals(MemoryPressure.NEUTRAL, result.pressure)
        val large = memoryPresentation(MemorySnapshot(1536 * MIB, 4 * GIB, 1229 * MIB, AuxiliaryMemoryKind.JVM_NON_HEAP))
        assertEquals(MemoryRow(MemoryMetric.HEAP, "1.5/4.0", MemoryUnit.GIB), large.rows[0])
        assertEquals(MemoryRow(MemoryMetric.HEAP_USAGE, "38", MemoryUnit.PERCENT), large.rows[1])
        assertEquals(MemoryRow(MemoryMetric.JVM_NON_HEAP, "1.2", MemoryUnit.GIB), large.rows[2])
    }
    @Test fun processOnlyUnknownAndInvalidFieldsDoNotPretendToBeHeap() {
        assertEquals(emptyList(), memoryPresentation(MemorySnapshot()).rows)
        assertEquals(emptyList(), memoryPresentation(MemorySnapshot(-1, 100, -1, processBytes = -1)).rows)
        val ios = memoryPresentation(MemorySnapshot(processBytes = 800 * MIB, processKind = ProcessMemoryKind.PHYSICAL_FOOTPRINT))
        assertEquals(listOf(MemoryRow(MemoryMetric.FOOTPRINT, "800", MemoryUnit.MIB)), ios.rows)
        assertEquals(MemoryPressure.NEUTRAL, ios.pressure)
        assertEquals(listOf(MemoryRow(MemoryMetric.HEAP, "0", MemoryUnit.MIB)), memoryPresentation(MemorySnapshot(0, 0)).rows)
    }
    @Test fun pressureChangesStrictlyAboveSixtyAndEightyPercent() {
        fun pressure(used: Long) = memoryPresentation(MemorySnapshot(used, 100)).pressure
        assertEquals(MemoryPressure.NEUTRAL, pressure(60))
        assertEquals(MemoryPressure.ELEVATED, pressure(61))
        assertEquals(MemoryPressure.ELEVATED, pressure(80))
        assertEquals(MemoryPressure.HIGH, pressure(81))
        assertEquals(MemoryPressure.NEUTRAL, memoryPresentation(MemorySnapshot(100)).pressure)
        assertEquals(MemoryPressure.HIGH, pressure(101))
    }
    @Test fun sizeConversionDoesNotOverflowForLargeSamples() {
        val value = memoryPresentation(MemorySnapshot(processBytes = Long.MAX_VALUE)).rows.single()
        assertEquals("8589934591.9", value.value)
        assertEquals(MemoryUnit.GIB, value.unit)
    }
}
