package io.github.mothlight.memory

import kotlin.test.*

class PlatformMemorySourceTest {
    @Test fun readsManagedHeapAndKeepsNonHeapSeparate() {
        val snapshot = PlatformMemorySource.read()
        assertTrue(assertIs<MemoryReading.Bytes>(snapshot.heapUsedBytes).value > 0)
        assertEquals(MemoryReading.Bytes(Runtime.getRuntime().maxMemory()), snapshot.heapLimitBytes)
        assertTrue(assertIs<MemoryReading.Bytes>(snapshot.auxiliaryBytes).value >= 0)
        assertEquals(AuxiliaryMemoryKind.JVM_NON_HEAP, snapshot.auxiliaryKind)
        if (System.getProperty("os.name").lowercase().let { it.contains("linux") }) {
            assertTrue(assertIs<MemoryReading.Bytes>(snapshot.processBytes).value > 0)
            assertEquals(ProcessMemoryKind.RESIDENT, snapshot.processKind)
        }
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            // JVM 17 exposes no portable working-set API; do not substitute virtual memory.
            assertEquals(MemoryReading.Unavailable, snapshot.processBytes)
        }
    }
}
