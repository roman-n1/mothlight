package io.github.mothlight

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Host-owned persistence. Calls are synchronous; use an in-memory preferences cache. */
interface MemoryOverlayStore {
    fun read(): Boolean
    fun write(enabled: Boolean)
}

/** No persistence: the owning settings instance keeps its own state. */
object TransientMemoryOverlayStore : MemoryOverlayStore {
    override fun read() = false
    override fun write(enabled: Boolean) = Unit
}

/** One setting per Mothlight instance, independent of event recording. */
class MemoryOverlaySettings(private val store: MemoryOverlayStore = TransientMemoryOverlayStore) {
    private val state = MutableStateFlow(readStoredValue())
    val enabled: StateFlow<Boolean> = state.asStateFlow()
    fun setEnabled(value: Boolean) {
        try { store.write(value) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Persistence is optional; keep the in-memory control usable. */ }
        state.value = value
    }
    private fun readStoredValue(): Boolean = try { store.read() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { false }
}
