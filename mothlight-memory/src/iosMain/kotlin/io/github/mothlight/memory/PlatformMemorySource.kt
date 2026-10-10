package io.github.mothlight.memory

import kotlinx.cinterop.ExperimentalForeignApi
import io.github.mothlight.memory.mach.mothlight_physical_footprint

@OptIn(ExperimentalForeignApi::class)
actual fun readMemorySnapshot(): MemorySnapshot {
    val footprint = mothlight_physical_footprint()
    return MemorySnapshot(
        processBytes = if (footprint > 0uL && footprint <= Long.MAX_VALUE.toULong()) MemoryReading.Bytes(footprint.toLong()) else MemoryReading.Unavailable,
        processKind = ProcessMemoryKind.PHYSICAL_FOOTPRINT,
    )
}
