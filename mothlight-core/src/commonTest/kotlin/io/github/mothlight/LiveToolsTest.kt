package io.github.mothlight

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LiveToolsTest {
    @Test fun databaseRefreshesAfterInvalidationAndStopsOnCancellation() = runTest {
        val changes = MutableSharedFlow<Unit>()
        var observers = 0
        var value = "before"
        var reads = 0
        val source = object : DatabaseSource {
            override suspend fun tables() = listOf("items")
            override suspend fun page(table: String, limit: Int, offset: Int): DatabasePage {
                reads++
                return DatabasePage(table, listOf("name"), listOf(listOf(value)), 1, offset, limit)
            }
            override fun changes() = flow {
                observers++
                try { emit(Unit); emitAll(changes) } finally { observers-- }
            }
        }
        val browser = DatabaseBrowser(source)
        val pages = mutableListOf<DatabasePage>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { browser.page("items", 10, 0).toList(pages) }
        assertEquals("before", pages.single().rows.single().single())
        assertEquals(1, observers)
        value = "after"
        changes.emit(Unit)
        runCurrent()
        assertEquals(listOf("before", "after"), pages.map { it.rows.single().single() })
        job.cancelAndJoin()
        assertEquals(0, observers)
        changes.emit(Unit)
        assertEquals(2, reads)
    }
    @Test fun sqlStatsSurviveFeedEvictionAndExcludePanelQueries() {
        var enabled = false
        val trace = SqlTraceRecorder(2, 2) { enabled }
        trace.record("SELECT hidden", 0, 1)
        assertTrue(trace.snapshot.value.events.isEmpty())
        enabled = true
        trace.record("SELECT a", 1, 3)
        trace.record("SELECT b", 2, 5, "SQLiteException")
        trace.record("SELECT a", 3, 7)
        trace.record(SqlTraceRecorder.PANEL_MARKER + "SELECT COUNT(*) FROM a", 4, 9)
        val snapshot = trace.snapshot.value
        assertEquals(listOf("SELECT b", "SELECT a"), snapshot.events.map { it.sql })
        assertEquals(SqlQueryStat("SELECT a", 2, 0, 10, 7), snapshot.queries.first { it.sql == "SELECT a" })
        assertEquals(1L, snapshot.queries.first { it.sql == "SELECT b" }.failures)
        val lastId = snapshot.events.last().id
        trace.record("SELECT c", 5, 1)
        assertEquals(setOf("SELECT a", "SELECT c"), trace.snapshot.value.queries.map { it.sql }.toSet())
        trace.clear()
        assertTrue(trace.snapshot.value.queries.isEmpty())
        trace.record("SELECT fresh", 6, 1)
        assertTrue(trace.snapshot.value.events.single().id > lastId)
    }
    @Test fun realtimeMasksBeforePublishingAndKeepsSeparateConnectionHistory() {
        var enabled = false
        val inspector = RealtimeInspector(2, Redactor(captureJsonBodies = true)) { enabled }
        inspector.record("chat", FrameDirection.INCOMING, "{}", 0)
        assertTrue(inspector.frames.value.isEmpty())
        enabled = true
        inspector.connection("chat", ConnectionState.CONNECTED, 1)
        inspector.record("chat", FrameDirection.OUTGOING, """{"token":"secret","n":1}""", 2)
        assertEquals("""{"token":"***","n":1}""", inspector.frames.value.single().payload)
        inspector.record("presence", FrameDirection.INCOMING, "{}", 3)
        inspector.record("chat", FrameDirection.INCOMING, "{}", 4)
        assertEquals(listOf("presence", "chat"), inspector.frames.value.map { it.channel })
        assertEquals(ConnectionState.CONNECTED, inspector.connections.value.single().state)
        val previous = inspector.frames.value.last().id
        inspector.clear()
        assertTrue(inspector.connections.value.isEmpty())
        inspector.record("new", FrameDirection.INCOMING, "{}", 5)
        assertTrue(inspector.frames.value.single().id > previous)
        val defaults = RealtimeInspector()
        defaults.record("test", FrameDirection.INCOMING, "secret", 0)
        assertNull(defaults.frames.value.single().payload)
    }
}
