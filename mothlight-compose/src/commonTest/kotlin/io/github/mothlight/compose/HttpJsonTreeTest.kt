package io.github.mothlight.compose

import kotlinx.serialization.json.*
import kotlin.test.*

class HttpJsonTreeTest {
    @Test fun opensObjectsAndArrayNodesIndependentlyWithUnambiguousPaths() {
        val document = assertIs<HttpJson.Structured>(parseHttpJson("""{"items":[{"id":1,"enabled":true}],"a/b":{"~":null},"empty":{},"literal":"null"}"""))
        val initial = jsonTreeRows(document.root, setOf(""))
        assertEquals(listOf("", "/items", "/a~1b", "/empty", "/literal"), initial.rows.map { it.path })
        val opened = jsonTreeRows(document.root, setOf("", "/items", "/items/0", "/a~1b"))
        assertEquals(JsonNodeKind.Boolean, opened.rows.first { it.path == "/items/0/enabled" }.kind)
        assertEquals(JsonNodeKind.Null, opened.rows.first { it.path == "/a~1b/~0" }.kind)
        assertEquals(JsonNodeKind.String, opened.rows.first { it.path == "/literal" }.kind)
        assertFalse(opened.rows.first { it.path == "/empty" }.expandable)
        assertEquals(1, jsonTreeRows(document.root, emptySet()).rows.size)
        assertFalse(jsonTreeRows(document.root, setOf("", "/items")).rows.any { it.path == "/items/0/id" })
    }
    @Test fun malformedOrScalarBodiesStayTextAndLargeTreesHaveExplicitLimits() {
        assertEquals(HttpJson.Plain, parseHttpJson("broken"))
        assertEquals(HttpJson.Plain, parseHttpJson("{broken"))
        assertEquals(HttpJson.Plain, parseHttpJson("null"))
        val root = JsonArray(List(100) { JsonPrimitive(it) })
        val rows = jsonTreeRows(root, setOf(""), maxRows = 10)
        assertEquals(10, rows.rows.size)
        assertTrue(rows.truncated)
        assertFalse(jsonTreeRows(root, emptySet(), maxRows = 10).truncated)
        assertNotEquals(jsonPointerChild("", "a/b"), jsonPointerChild("/a", "b"))
    }
}
