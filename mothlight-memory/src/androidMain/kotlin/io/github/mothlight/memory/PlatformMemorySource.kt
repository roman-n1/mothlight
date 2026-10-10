package io.github.mothlight.memory

import android.os.Debug

actual fun readMemorySnapshot(): MemorySnapshot {
    val runtime = Runtime.getRuntime()
    return MemorySnapshot(
        heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
        heapLimitBytes = runtime.maxMemory().takeIf { it > 0 && it != Long.MAX_VALUE },
        auxiliaryBytes = Debug.getNativeHeapAllocatedSize().takeIf { it >= 0 },
        auxiliaryKind = AuxiliaryMemoryKind.NATIVE_HEAP,
    )
}
