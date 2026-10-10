package io.github.mothlight.memory

import java.io.File
import java.lang.management.ManagementFactory

actual fun readMemorySnapshot(): MemorySnapshot {
    val runtime = Runtime.getRuntime()
    val nonHeap = try { MemoryReading.bytes(ManagementFactory.getMemoryMXBean().nonHeapMemoryUsage.used) } catch (_: Exception) { MemoryReading.Unavailable }
    return MemorySnapshot(
        heapUsedBytes = MemoryReading.bytes(runtime.totalMemory() - runtime.freeMemory()),
        heapLimitBytes = MemoryReading.limit(runtime.maxMemory()),
        auxiliaryBytes = nonHeap,
        auxiliaryKind = AuxiliaryMemoryKind.JVM_NON_HEAP,
        processBytes = processResidentBytes(),
    )
}

private fun processResidentBytes(): MemoryReading = try {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    when {
        os.contains("linux") -> File("/proc/self/status").useLines { lines ->
            val line = lines.firstOrNull { it.startsWith("VmRSS:") } ?: return@useLines MemoryReading.Unavailable
            val kb = line.removePrefix("VmRSS:").trim().removeSuffix("kB").trim().toLongOrNull()
                ?: return@useLines MemoryReading.Unavailable
            if (kb >= 0 && kb <= Long.MAX_VALUE / 1024) MemoryReading.Bytes(kb * 1024) else MemoryReading.Unavailable
        }
        else -> MemoryReading.Unavailable
    }
} catch (_: Exception) {
    MemoryReading.Unavailable
}
