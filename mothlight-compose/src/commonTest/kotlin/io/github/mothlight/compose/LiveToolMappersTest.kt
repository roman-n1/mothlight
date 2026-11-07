package io.github.mothlight.compose

import io.github.mothlight.*
import kotlin.test.*

class LiveToolMappersTest {
    @Test fun paginationHandlesLastPartialPageAndLocalizedNull() {
        val page = DatabasePage("items", listOf("id", "name"), listOf(listOf(DatabaseCell.Text("51"), DatabaseCell.SqlNull)), 51, 50, 50)
        assertFalse(hasNextPage(page))
        assertTrue(hasPreviousPage(page))
        assertEquals(0, previousOffset(page))
        assertEquals("empty", cellText(DatabaseCell.SqlNull, "empty"))
        assertTrue(hasNextPage(page.copy(totalRows = 101)))
        assertEquals(100, nextOffset(page))
    }
    @Test fun statsSortByTotalCostAndSearchIgnoresCase() {
        val state = SqlTraceSnapshot(queries = listOf(SqlQueryStat("SELECT cheap", 5, 0, 10, 2), SqlQueryStat("SELECT slow", 1, 0, 50, 50)))
        assertEquals(listOf("SELECT slow", "SELECT cheap"), sqlStats(state, "select").map { it.sql })
        assertEquals(1, sqlStats(state, "SLOW").size)
    }

    @Test fun sqlFormattingKeepsQuotedTextAndCommentsTogether() {
        val sql = "SELECT 'a  WHERE b', [FROM x] FROM items /* ORDER BY x */ WHERE id = ? -- LIMIT 4"
        assertEquals("SELECT 'a  WHERE b', [FROM x]\nFROM items /* ORDER BY x */\nWHERE id = ? -- LIMIT 4",
            formatRecordedSql(sql))
    }
}
