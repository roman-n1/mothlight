package io.github.mothlight.compose

import io.github.mothlight.*
import kotlin.test.*

class LiveToolMappersTest {
    @Test fun paginationHandlesLastPartialPageAndLocalizedNull() {
        val page = DatabasePage("items", listOf("id", "name"), listOf(listOf("51", null)), 51, 50, 50)
        assertFalse(hasNextPage(page))
        assertTrue(hasPreviousPage(page))
        assertEquals(0, previousOffset(page))
        assertEquals(listOf("id: 51\nname: empty"), databaseRows(page, "empty"))
        assertTrue(hasNextPage(page.copy(totalRows = 101)))
        assertEquals(100, nextOffset(page))
    }
    @Test fun statsSortByTotalCostAndSearchIgnoresCase() {
        val state = SqlTraceSnapshot(queries = listOf(SqlQueryStat("SELECT cheap", 5, 0, 10, 2), SqlQueryStat("SELECT slow", 1, 0, 50, 50)))
        assertEquals(listOf("SELECT slow", "SELECT cheap"), sqlStats(state, "select").map { it.sql })
        assertEquals(1, sqlStats(state, "SLOW").size)
        val frames = listOf(RealtimeFrame(1, "chat", FrameDirection.INCOMING, null, 1), RealtimeFrame(2, "presence", FrameDirection.INCOMING, null, 2), RealtimeFrame(3, "chat", FrameDirection.OUTGOING, null, 3))
        assertEquals(listOf(3L, 1L), realtimeFrames(frames, "CHAT").map { it.id })
    }
}
