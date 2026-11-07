package io.github.mothlight.sample

import io.github.mothlight.Status
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class ArticleReaderTest {
    @Test fun fullTextIsRecordedCachedAndSurvivesFeedRefreshAndOfflineRestart(): Unit = runBlocking {
        val directory = createTempDirectory("leaf-article").toFile()
        val file = directory.resolve("reader.db")
        try {
            val session = DemoSession(file, fixtureEngine())
            try {
                session.initialize()
                assertTrue(session.loadArticle(1))
                val text = withTimeout(5000) { session.articleContent(1).first { it != null } }!!.text
                assertContains(text, "complete article text")
                assertContains(text, "val answer = 42")
                assertTrue(session.mothlight.network.transactions.value.any {
                    it.url == "https://dev.to/api/articles/1" && (it.status as? Status.Completed)?.code == 200
                })
                session.refresh()
                assertEquals(text, session.articleContent(1).first()!!.text)
            } finally { session.close() }
            val offline = DemoSession(file, MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable) })
            try {
                assertFalse(offline.loadArticle(1))
                assertContains(offline.articleContent(1).first()!!.text, "complete article text")
                assertTrue(offline.mothlight.network.transactions.value.any { (it.status as? Status.Completed)?.code == 503 })
            } finally { offline.close() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun htmlBecomesReadableTextAndRejectsWrongArticle() {
        val json = """{"id":1,"body_html":"<h2>Title</h2><p>A &amp; B <strong>bold</strong>.</p><ul><li>First</li><li>Second</li></ul><script>secret()</script>"}"""
        val text = articleText(json, 1)
        assertContains(text, "A & B bold.")
        assertContains(text, "• First")
        assertFalse(text.contains("secret()"))
        assertFalse(text.contains("<strong>"))
        assertFailsWith<IllegalArgumentException> { articleText(json, 2) }
    }
}
