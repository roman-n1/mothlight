package io.github.mothlight.memory

import kotlinx.cinterop.ExperimentalForeignApi
import io.github.mothlight.memory.mach.mothlight_physical_footprint

@OptIn(ExperimentalForeignApi::class)
actual fun readMemorySnapshot(): MemorySnapshot {
    val footprint = mothlight_physical_footprint()
    return MemorySnapshot(
        processBytes = footprint.takeIf { it > 0uL && it <= Long.MAX_VALUE.toULong() }?.toLong(),
        processKind = ProcessMemoryKind.PHYSICAL_FOOTPRINT,
    )
}
