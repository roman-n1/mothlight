package io.github.mothlight.memory

import io.github.mothlight.MemoryOverlaySettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Metrics are distinct measurements; never add them together. Null means unavailable. */
data class MemorySnapshot(
    val heapUsedBytes: Long? = null,
    val heapLimitBytes: Long? = null,
    val auxiliaryBytes: Long? = null,
    val auxiliaryKind: AuxiliaryMemoryKind = AuxiliaryMemoryKind.NATIVE_HEAP,
    val processBytes: Long? = null,
    val processKind: ProcessMemoryKind = ProcessMemoryKind.RESIDENT,
)
enum class AuxiliaryMemoryKind { NATIVE_HEAP, JVM_NON_HEAP }
enum class ProcessMemoryKind { RESIDENT, PHYSICAL_FOOTPRINT }
fun interface MemorySource { fun read(): MemorySnapshot }

/** Cold observation: no scope, retained history, global hooks or work before collection. */
fun observeMemory(
    settings: MemoryOverlaySettings,
    source: MemorySource,
    intervalMillis: Long = 1_000,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Flow<MemorySnapshot> = flowOf(MemorySnapshot())
