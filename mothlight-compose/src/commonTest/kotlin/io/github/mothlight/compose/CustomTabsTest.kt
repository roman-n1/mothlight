package io.github.mothlight.compose

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import io.github.mothlight.Mothlight
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class CustomTabsTest {
    @Test fun customTabsAppendToBuiltInsInOrderAndKeepTheirCallbacks() {
        val mothlight = Mothlight()
        val plugin = MothlightTool("plugin", { "Plugin" }, {})
        val calls = mutableListOf<String>()
        val marker = object : Modifier.Element {}
        val tools = mothlightTools(mothlight) {
            tab("cache", title = { calls += "title"; "Cache" }) { modifier ->
                assertSame(marker, modifier)
                calls += "content"
                DisposableEffect(Unit) { onDispose { calls += "disposed" } }
            }
            tool(plugin)
            tab("flags", title = { "Flags" }) { error("Inactive tab was composed") }
        }
        assertEquals(defaultMothlightTools(mothlight).entries.map { it.id } +
            listOf("cache", "plugin", "flags"), tools.entries.map { it.id })
        assertSame(plugin, tools.entries.first { it.id == "plugin" })
        assertTrue(calls.isEmpty(), "Registration must not compose a tab")
        val selected = resolveToolId(tools.entries.map { it.id }, ToolSelection.Named("cache"))
        val tab = tools.entries.first { it.id == selected }
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(NoOpApplier(), recomposer)
        try {
            composition.setContent {
                assertEquals("Cache", tab.title())
                tab.content(marker)
            }
            assertEquals(listOf("title", "content"), calls)
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
        assertEquals(listOf("title", "content", "disposed"), calls)
    }

    @Test fun registrationRejectsBlankAndCollidingIds() {
        val mothlight = Mothlight()
        assertFailsWith<IllegalArgumentException> {
            mothlightTools(mothlight) { tab(" ", { "Blank" }) {} }
        }
        assertFailsWith<IllegalArgumentException> {
            mothlightTools(mothlight) { tab("http", { "Duplicate built-in" }) {} }
        }
        assertFailsWith<IllegalArgumentException> {
            mothlightTools(mothlight) {
                tab("cache", { "First" }) {}
                tool(MothlightTool("cache", { "Second" }, {}))
            }
        }
    }

    @Test fun noCustomTabsKeepsDefaultsAndRegistrationsAreIndependent() {
        val mothlight = Mothlight()
        val first = mothlightTools(mothlight) { tab("cache", { "Cache" }) {} }
        val second = mothlightTools(mothlight) {}
        assertEquals(defaultMothlightTools(mothlight).entries.map { it.id }, second.entries.map { it.id })
        assertEquals("cache", first.entries.last().id)
        assertFalse(second.entries.any { it.id == "cache" })
    }
}

private class NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}
