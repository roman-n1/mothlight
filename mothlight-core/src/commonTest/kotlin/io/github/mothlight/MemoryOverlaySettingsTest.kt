package io.github.mothlight

import kotlin.test.*

class MemoryOverlaySettingsTest {
    @Test fun settingIsIsolatedPersistentAndIndependentOfRecordingAndClear() {
        var saved = true
        val writes = mutableListOf<Boolean>()
        val store = object : MemoryOverlayStore {
            override fun read() = saved
            override fun write(enabled: Boolean) { saved = enabled; writes += enabled }
        }
        val first = Mothlight(memoryOverlayStore = store)
        val other = Mothlight()
        assertTrue(first.memoryOverlay.enabled.value)
        assertFalse(other.memoryOverlay.enabled.value)
        first.memoryOverlay.setEnabled(false)
        assertFalse(first.memoryOverlay.enabled.value)
        assertEquals(listOf(false), writes)
        first.memoryOverlay.setEnabled(true)
        first.clear()
        first.setEnabled(false)
        assertTrue(first.memoryOverlay.enabled.value)
        assertFalse(other.memoryOverlay.enabled.value)
        assertEquals(listOf(false, true), writes)
        assertTrue(Mothlight(memoryOverlayStore = store).memoryOverlay.enabled.value)
    }
}
