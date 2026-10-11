package io.github.mothlight

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class StabilityTest {
    @Test fun failedSettingsPersistenceFallsBackToWorkingInMemoryControls() {
        var writes = 0
        val settings = MemoryOverlaySettings(object : MemoryOverlayStore {
            override fun read(): Boolean = error("storage offline")
            override fun write(enabled: Boolean) { writes++; error("disk full") }
        })
        assertFalse(settings.enabled.value)
        settings.setEnabled(true)
        assertTrue(settings.enabled.value)
        settings.setEnabled(false)
        assertFalse(settings.enabled.value)
        assertEquals(2, writes)
    }

    @Test fun settingsPreserveCancellationAndFatalErrors() {
        val cancelled = CancellationException("cancelled")
        assertSame(cancelled, assertFailsWith<CancellationException> {
            MemoryOverlaySettings(object : MemoryOverlayStore {
                override fun read(): Boolean = throw cancelled
                override fun write(enabled: Boolean) = Unit
            })
        })
        val settings = MemoryOverlaySettings(object : MemoryOverlayStore {
            override fun read() = false
            override fun write(enabled: Boolean): Unit = throw cancelled
        })
        assertSame(cancelled, assertFailsWith<CancellationException> { settings.setEnabled(true) })
        assertFalse(settings.enabled.value)
        val fatal = AssertionError("fatal")
        assertSame(fatal, assertFailsWith<AssertionError> {
            MemoryOverlaySettings(object : MemoryOverlayStore {
                override fun read(): Boolean = throw fatal
                override fun write(enabled: Boolean) = Unit
            })
        })
    }

    @Test fun databaseSubscriptionCreationIsLazyCatchableAndRetriedOnCollection() = runTest {
        var subscriptions = 0
        val failure = IllegalStateException("database closed")
        val source = object : DatabaseSource {
            override suspend fun tables() = listOf("items")
            override suspend fun page(table: String, limit: Int, offset: Int) = DatabasePage(table, emptyList(), emptyList(), 0, offset, limit)
            override fun changes(): Flow<Unit> {
                subscriptions++
                if (subscriptions <= 2) throw failure
                return flowOf(Unit)
            }
        }
        val browser = DatabaseBrowser(source)
        val tables = browser.tables()
        val page = browser.page("items")
        assertEquals(0, subscriptions)
        val errors = mutableListOf<Throwable>()
        tables.catch { errors += it }.collect()
        page.catch { errors += it }.collect()
        assertEquals(2, errors.size)
        errors.forEach { assertSame(failure, it) }
        assertEquals(listOf("items"), tables.single())
        assertEquals("items", page.single().table)
        assertEquals(4, subscriptions)
    }

    @Test fun jsonDepthIsBoundedBeforeRecursiveParsingAndScrubbing() {
        val redactor = Redactor(captureJsonBodies = true)
        val accepted = "[".repeat(64) + "0" + "]".repeat(64)
        assertEquals(CapturedBody.Text(accepted), redactor.body(accepted))
        val deep = "[".repeat(65) + "0" + "]".repeat(65)
        assertEquals(CapturedBody.TooLarge, redactor.body(deep))
        assertEquals(CapturedBody.TooLarge, redactor.body("[".repeat(2000) + "0" + "]".repeat(2000)))
        val quoted = """{"value":"${"[".repeat(80)}\"${"}".repeat(80)}","token":"secret"}"""
        val scrubbed = assertIs<CapturedBody.Text>(redactor.body(quoted)).value
        assertTrue(scrubbed.contains("[".repeat(80)))
        assertFalse(scrubbed.contains("secret"))
        assertEquals(CapturedBody.Text("{}"), redactor.body("{}"))
    }
}
