package io.github.mothlight.memory

import io.github.mothlight.MemoryOverlaySettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryObservationTest {
    @Test fun samplesImmediatelyThenPeriodicallyOnlyWhileEnabledAndCollected() = runTest {
        val settings = MemoryOverlaySettings()
        var reads = 0
        val samples = mutableListOf<MemorySnapshot>()
        val flow = observeMemory(settings, MemorySource { MemorySnapshot(heapUsedBytes = MemoryReading.bytes((++reads).toLong())) }, 1000, StandardTestDispatcher(testScheduler))
        assertEquals(0, reads)
        val job = backgroundScope.launch { flow.toList(samples) }
        runCurrent()
        assertEquals(0, reads)
        settings.setEnabled(true)
        runCurrent()
        assertEquals(MemoryReading.Bytes(1L), samples.last().heapUsedBytes)
        advanceTimeBy(999); runCurrent()
        assertEquals(1, reads)
        advanceTimeBy(1); runCurrent()
        assertEquals(MemoryReading.Bytes(2L), samples.last().heapUsedBytes)
        settings.setEnabled(false); runCurrent()
        assertEquals(MemorySnapshot(), samples.last())
        advanceTimeBy(3000); runCurrent()
        assertEquals(2, reads)
        settings.setEnabled(true); runCurrent()
        assertEquals(MemoryReading.Bytes(3L), samples.last().heapUsedBytes)
        job.cancelAndJoin()
        advanceTimeBy(3000); runCurrent()
        assertEquals(3, reads)
    }
    @Test fun unavailableReadingClearsStaleSampleAndNextTickRecovers() = runTest {
        val settings = MemoryOverlaySettings().apply { setEnabled(true) }
        var reads = 0
        val values = mutableListOf<MemorySnapshot>()
        val job = backgroundScope.launch {
            observeMemory(settings, MemorySource {
                reads++
                if (reads == 2) error("unavailable")
                MemorySnapshot(heapUsedBytes = MemoryReading.bytes(reads.toLong()))
            }, 100, StandardTestDispatcher(testScheduler)).toList(values)
        }
        runCurrent()
        assertEquals(MemoryReading.Bytes(1L), values.last().heapUsedBytes)
        advanceTimeBy(100); runCurrent()
        assertEquals(MemorySnapshot(), values.last())
        advanceTimeBy(100); runCurrent()
        assertEquals(MemoryReading.Bytes(3L), values.last().heapUsedBytes)
        job.cancelAndJoin()
        assertFailsWith<IllegalArgumentException> { observeMemory(settings, MemorySource { MemorySnapshot() }, 0) }
    }
    @Test fun cancellationFromSourceIsNotSwallowed() = runTest {
        val settings = MemoryOverlaySettings().apply { setEnabled(true) }
        val job = launch {
            observeMemory(settings, MemorySource { throw CancellationException("cancel read") }, dispatcher = StandardTestDispatcher(testScheduler)).collect()
        }
        runCurrent()
        assertTrue(job.isCancelled)
    }
}
