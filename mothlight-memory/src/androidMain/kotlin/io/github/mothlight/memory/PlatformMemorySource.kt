package io.github.mothlight.memory

import android.os.Debug

actual fun readMemorySnapshot(): MemorySnapshot {
    val runtime = Runtime.getRuntime()
    return MemorySnapshot(
        heapUsedBytes = MemoryReading.bytes(runtime.totalMemory() - runtime.freeMemory()),
        heapLimitBytes = MemoryReading.limit(runtime.maxMemory()),
        auxiliaryBytes = MemoryReading.bytes(Debug.getNativeHeapAllocatedSize()),
        auxiliaryKind = AuxiliaryMemoryKind.NATIVE_HEAP,
    )
}
