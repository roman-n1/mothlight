package io.github.mothlight.memory

import kotlin.test.*

class PlatformMemorySourceTest {
    @Test fun physicalFootprintIsProcessMemoryWithoutInventedHeapLimit() {
        val snapshot = PlatformMemorySource.read()
        assertTrue(assertIs<MemoryReading.Bytes>(snapshot.processBytes).value > 0)
        assertEquals(ProcessMemoryKind.PHYSICAL_FOOTPRINT, snapshot.processKind)
        assertEquals(MemoryReading.Unavailable, snapshot.heapUsedBytes)
        assertEquals(MemoryReading.Unavailable, snapshot.heapLimitBytes)
        assertEquals(MemoryReading.Unavailable, snapshot.auxiliaryBytes)
    }
}
