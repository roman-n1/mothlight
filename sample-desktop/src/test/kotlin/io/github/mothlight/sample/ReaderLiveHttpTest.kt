package io.github.mothlight.sample

import io.github.mothlight.CapturedBody
import io.github.mothlight.Status
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeTrue
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class ReaderLiveHttpTest {
    /** Opt-in smoke test for the actual CIO engine, DEV API, Room and Mothlight observer. */
    @Test fun liveArticlesAndResponseBodiesReachTheInspector(): Unit = runBlocking {
        assumeTrue(System.getenv("MOTHLIGHT_LIVE_HTTP") == "1")
        val directory = createTempDirectory("leaf-live").toFile()
        val session = DemoSession(directory.resolve("reader.db"))
        try {
            session.initialize()
            assertFalse(session.error.value, "Live DEV refresh failed: ${session.mothlight.network.transactions.value.map { it.status }}")
            val rows = withTimeout(10_000) { session.articles.first { it.isNotEmpty() } }
            assertTrue(rows.all { it.sourceUrl.startsWith("https://dev.to/") })
            val requests = withTimeout(10_000) {
                session.mothlight.network.transactions.first { items ->
                    items.size == 3 && items.all { it.responseBody is CapturedBody.Text }
                }
            }
            assertTrue(requests.all { (it.status as? Status.Completed)?.code == 200 })
            assertTrue(requests.all { it.url == "https://dev.to/api/articles" })
            assertTrue(session.loadArticle(rows.first().id))
            val full = withTimeout(10_000) { session.articleContent(rows.first().id).first { it != null } }
            assertTrue(full!!.text.isNotBlank())
            val detail = withTimeout(10_000) { session.mothlight.network.transactions.first { list ->
                list.any { it.url.endsWith("/${rows.first().id}") && it.status is Status.Completed }
            } }.last()
            assertEquals(200, (detail.status as Status.Completed).code)
            println("Full article loaded and recorded: ${full.text.length} characters")
            println("Live DEV: ${rows.size} articles stored; ${requests.size} HTTP 200 responses with JSON captured")
        } finally { session.close(); directory.deleteRecursively() }
    }
}
