package io.github.mothlight.compose

import io.github.mothlight.*
import kotlin.test.*

class ToolMappersTest {
    @Test fun searchIsCaseInsensitiveAndNewestFirstWithoutChangingSource() {
        val source = listOf(tx("1", "GET", "/alpha"), tx("2", "POST", "/beta"), tx("3", "GET", "/alpha"))
        assertEquals(listOf("3", "1"), networkRows(source, "get /ALPHA").map { it.id })
        assertEquals(listOf("1", "2", "3"), source.map { it.id })
        assertNull(selectedTransaction(source, "missing"))
        assertEquals(source[1], selectedTransaction(source, "2"))
    }
    @Test fun detailsContainActualLineBreaks() {
        assertTrue(requestDetails(tx("1", "GET", "/a")).contains("\n"))
        assertFalse(requestDetails(tx("1", "GET", "/a")).contains("\\n"))
    }
    @Test fun toolsRejectDuplicatesEmptySetsAndBlankIds() {
        fun tool(id: String) = MothlightTool(id, { "test" }, {})
        assertFailsWith<IllegalArgumentException> { MothlightTools(emptyList()) }
        assertFailsWith<IllegalArgumentException> { MothlightTools(listOf(tool(" "))) }
        assertFailsWith<IllegalArgumentException> { MothlightTools(listOf(tool("a"), tool("a"))) }
        assertEquals(listOf("b", "a"), MothlightTools(listOf(tool("b"), tool("a"))).entries.map { it.id })
    }
    private fun tx(id: String, method: String, url: String) = NetworkTransaction(id, id.toLong(), method, url,
        emptyMap(), null, 200, emptyMap(), null, 0, 10, Status.Completed)
}
