package io.github.mothlight.memory

import kotlin.test.*

class PlatformMemorySourceTest {
    @Test fun physicalFootprintIsProcessMemoryWithoutInventedHeapLimit() {
        val snapshot = PlatformMemorySource.read()
        assertTrue(assertNotNull(snapshot.processBytes) > 0)
        assertEquals(ProcessMemoryKind.PHYSICAL_FOOTPRINT, snapshot.processKind)
        assertNull(snapshot.heapUsedBytes)
        assertNull(snapshot.heapLimitBytes)
        assertNull(snapshot.auxiliaryBytes)
    }
}
