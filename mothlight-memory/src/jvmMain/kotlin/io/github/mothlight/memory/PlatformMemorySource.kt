package io.github.mothlight.memory

import java.io.File
import java.lang.management.ManagementFactory

actual fun readMemorySnapshot(): MemorySnapshot {
    val runtime = Runtime.getRuntime()
    val nonHeap = try { ManagementFactory.getMemoryMXBean().nonHeapMemoryUsage.used } catch (_: Exception) { null }
    return MemorySnapshot(
        heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
        heapLimitBytes = runtime.maxMemory().takeIf { it > 0 && it != Long.MAX_VALUE },
        auxiliaryBytes = nonHeap?.takeIf { it >= 0 },
        auxiliaryKind = AuxiliaryMemoryKind.JVM_NON_HEAP,
        processBytes = processResidentBytes(),
    )
}

private fun processResidentBytes(): Long? = try {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    when {
        os.contains("linux") -> File("/proc/self/status").useLines { lines ->
            val line = lines.firstOrNull { it.startsWith("VmRSS:") }
            val kb = line?.removePrefix("VmRSS:")?.trim()?.removeSuffix("kB")?.trim()?.toLongOrNull()
            kb?.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1024 }?.times(1024)
        }
        else -> null
    }
} catch (_: Exception) {
    null
}
