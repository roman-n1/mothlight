package io.github.mothlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Host-owned persistence. Calls are synchronous; use an in-memory preferences cache. */
interface MemoryOverlayStore {
    fun read(): Boolean
    fun write(enabled: Boolean)
}

/** One setting per Mothlight instance, independent of event recording. */
class MemoryOverlaySettings(private val store: MemoryOverlayStore? = null) {
    private val state = MutableStateFlow(store?.read() ?: false)
    val enabled: StateFlow<Boolean> = state.asStateFlow()
    fun setEnabled(value: Boolean) {
        store?.write(value)
        state.value = value
    }
}
