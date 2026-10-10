package io.github.mothlight.memory

import java.lang.management.ManagementFactory
import kotlin.test.*

class PlatformMemorySourceTest {
    @Test fun readsManagedHeapAndKeepsNonHeapSeparate() {
        val snapshot = PlatformMemorySource.read()
        assertTrue(assertNotNull(snapshot.heapUsedBytes) > 0)
        assertEquals(Runtime.getRuntime().maxMemory(), snapshot.heapLimitBytes)
        assertTrue(assertNotNull(snapshot.auxiliaryBytes) >= 0)
        assertEquals(AuxiliaryMemoryKind.JVM_NON_HEAP, snapshot.auxiliaryKind)
        if (System.getProperty("os.name").lowercase().let { it.contains("linux") || it.contains("windows") }) {
            assertTrue(assertNotNull(snapshot.processBytes) > 0)
            assertEquals(ProcessMemoryKind.RESIDENT, snapshot.processKind)
        }
    }
}
