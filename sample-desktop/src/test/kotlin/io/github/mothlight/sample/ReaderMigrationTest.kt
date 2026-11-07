package io.github.mothlight.sample

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class ReaderMigrationTest {
    @Test fun upgradeKeepsSubscriptionsAndSavedDemoArticles(): Unit = runBlocking {
        val directory = createTempDirectory("leaf-migration").toFile()
        val file = directory.resolve("reader.db")
        try {
            val connection = BundledSQLiteDriver().open(file.absolutePath)
            try {
                connection.execSQL("CREATE TABLE publications (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL, subscribed INTEGER NOT NULL)")
                connection.execSQL("CREATE TABLE articles (id INTEGER NOT NULL PRIMARY KEY, publicationId TEXT NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL, body TEXT NOT NULL, minutes INTEGER NOT NULL)")
                connection.execSQL("CREATE TABLE bookmarks (articleId INTEGER NOT NULL PRIMARY KEY)")
                connection.execSQL("INSERT INTO publications VALUES ('technology', 'Old title', 'Old description', 0)")
                connection.execSQL("INSERT INTO articles VALUES (99, 'technology', 'Saved demo', 'Summary', 'Body', 2)")
                connection.execSQL("INSERT INTO articles VALUES (98, 'technology', 'Unsaved demo', 'Summary', 'Body', 2)")
                connection.execSQL("INSERT INTO bookmarks VALUES (99)")
                connection.execSQL("PRAGMA user_version = 1")
            } finally { connection.close() }
            val session = DemoSession(file, fixtureEngine())
            try {
                session.initialize()
                assertFalse(session.error.value)
                val rows = withTimeout(10_000) { session.articles.first { it.size == 7 } }
                val sources = withTimeout(10_000) { session.publications.first { it.size == 3 } }
                assertTrue(rows.any { it.id == 99L && it.sourceUrl.isEmpty() })
                assertFalse(rows.any { it.id == 98L })
                assertFalse(sources.single { it.id == "technology" }.subscribed)
                assertEquals("DEV · Kotlin", sources.single { it.id == "technology" }.title)
                assertEquals(99L, withTimeout(10_000) { session.bookmarks.first { it.isNotEmpty() } }.single().articleId)
            } finally { session.close() }
        } finally { directory.deleteRecursively() }
    }
}
