package io.github.mothlight.compose

import io.github.mothlight.*
import kotlin.test.*

class ToolMappersTest {
    @Test fun searchIsCaseInsensitiveAndNewestFirstWithoutChangingSource() {
        val source = listOf(tx("1", "GET", "/alpha"), tx("2", "POST", "/beta"), tx("3", "GET", "/alpha"))
        assertEquals(listOf("3", "1"), networkRows(source, "get /ALPHA").map { it.id })
        assertEquals(listOf("1", "2", "3"), source.map { it.id })
        assertEquals(RequestDetails.Prompt, selectedTransaction(source, NetworkSelection.Unselected))
        assertEquals(RequestDetails.Prompt, selectedTransaction(source, NetworkSelection.Request("missing")))
        assertEquals(RequestDetails.Ready(source[1]), selectedTransaction(source, NetworkSelection.Request("2")))
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
        emptyMap(), CapturedBody.NotCaptured, emptyMap(), CapturedBody.NotCaptured, 0, Status.Completed(200, 10))
}
