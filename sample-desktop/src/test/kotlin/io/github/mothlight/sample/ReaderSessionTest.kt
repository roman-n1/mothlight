package io.github.mothlight.sample

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class ReaderSessionTest {
    @Test fun subscriptionsBookmarksAndCachedArticlesSurviveRestart(): Unit = runBlocking {
        val directory = createTempDirectory("leaf-test").toFile()
        val file = directory.resolve("reader.db")
        try {
            val first = DemoSession(file, fixtureEngine())
            try {
                first.initialize()
                withTimeout(10_000) { first.articles.first { it.size == 6 } }
                first.toggleSubscription("city")
                first.toggleBookmark(3)
                withTimeout(10_000) { first.publications.first { it.any { row -> row.id == "city" && row.subscribed } } }
                withTimeout(10_000) { first.bookmarks.first { it.any { row -> row.articleId == 3L } } }
                first.refresh()
                assertFalse(first.error.value)
                assertTrue(first.bookmarks.value.any { it.articleId == 3L })
                assertTrue(first.mothlight.network.transactions.value.isNotEmpty())
            } finally { first.close() }
            val second = DemoSession(file, fixtureEngine())
            try {
                second.initialize()
                withTimeout(10_000) { second.articles.first { it.size == 6 } }
                val pubs = withTimeout(10_000) { second.publications.first { it.isNotEmpty() } }
                val saved = withTimeout(10_000) { second.bookmarks.first { it.isNotEmpty() } }
                assertTrue(pubs.single { it.id == "city" }.subscribed)
                assertEquals(listOf(3L), saved.map { it.articleId })
                second.refresh()
                assertFalse(second.error.value)
                second.toggleBookmark(3)
                withTimeout(10_000) { second.bookmarks.first { it.isEmpty() } }
            } finally { second.close() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun feedUsesSubscriptionsAndSavedListUsesBookmarks() {
        val pubs = publications.map { it.copy(subscribed = it.id == "design") }
        assertEquals(setOf(1L, 4L), visibleArticles(ReaderDestination.Feed, catalog.map { it.copy(sourceUrl = "https://dev.to/test") }, pubs, emptyList()).map { it.id }.toSet())
        assertEquals(listOf(3L), visibleArticles(ReaderDestination.Saved, catalog, pubs, listOf(Bookmark(3))).map { it.id })
        val parsed = decodeArticles(catalogJson("design"), "design")
        assertEquals(listOf(1L, 4L), parsed.map { it.id })
        assertTrue(parsed.all { it.sourceUrl.startsWith("https://dev.to/") && it.author == "Test Author" })
    }
}
