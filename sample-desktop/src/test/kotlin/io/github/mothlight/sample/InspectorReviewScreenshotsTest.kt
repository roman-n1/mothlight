package io.github.mothlight.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.mothlight.room3.RoomDatabaseSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import io.github.mothlight.*
import io.github.mothlight.compose.*
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Reproducible current-UI references; all URLs and records are synthetic. */
@OptIn(ExperimentalTestApi::class)
class InspectorReviewScreenshotsTest {
    private val originalLocale = Locale.getDefault()

    @BeforeTest fun useRussianLocale() {
        Locale.setDefault(Locale.forLanguageTag("ru"))
    }

    @AfterTest fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test fun englishSqlAndStartup() {
        Locale.setDefault(Locale.ENGLISH)
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val mothlight = reviewFixture()
            val state = MothlightPanelState().apply { selectTool("sql") }
            setContent { DemoTheme(false) { MothlightPanel(mothlight, Modifier.fillMaxSize(), state = state) } }
            onNodeWithText("Summary").assertIsDisplayed()
            onNodeWithText("Total, ms").assertIsDisplayed()
            captureReview("mothlight-sql-summary-en-stage4")
            onNodeWithTag("sql-query:SELECT * FROM products WHERE category = ? ORDER BY price DESC").performClick()
            onNodeWithText("Copy SQL").assertIsDisplayed()
            onNodeWithText("Retained executions · 4").assertIsDisplayed()
            runOnIdle { state.selectTool("startup") }
            onNodeWithText("Time since first mark").assertIsDisplayed()
            onNodeWithText("230 ms · last mark").assertIsDisplayed()
            captureReview("mothlight-startup-timeline-en-stage4")
        }
    }

    @Test fun desktopTools() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val mothlight = reviewFixture()
        val state = MothlightPanelState()
        val tools = mothlightTools(mothlight) { tool(databaseTool(reviewDatabase())) }
        setContent { DemoTheme(false) { MothlightPanel(mothlight, Modifier.fillMaxSize(), tools, state) } }
        captureReview("mothlight-http-list")
        onNodeWithTag("http-row-3").performClick()
        onNodeWithTag("http-tab-Response").performClick()
        captureReview("mothlight-http-details")
        onNodeWithTag("json-node:/items").performScrollTo().performClick()
        onNodeWithTag("json-node:/items/0").performScrollTo().performClick()
        onNodeWithTag("http-payload").performScrollToKey("json:/items/0/title")
        onNodeWithTag("json-node:/items/0/title").assertIsDisplayed()
        captureReview("mothlight-http-json-expanded")
        runOnIdle { state.selectTool("logs") }
        onNodeWithText("Refresh failed; cached catalog remains visible").assertIsDisplayed()
        captureReview("mothlight-logs")
        onNodeWithTag("log-row:4").performClick()
        runOnIdle { mothlight.logs.record(LogEntry("5", 9500, LogLevel.WARN, "Network", "A retry arrived while reading")) }
        onNodeWithTag("logs-pending", useUnmergedTree = true).assertIsDisplayed()
        captureReview("mothlight-logs-reading")
        runOnIdle { state.selectTool("sql") }
        val select = "SELECT * FROM products WHERE category = ? ORDER BY price DESC"
        onNodeWithTag("sql-query:$select").assertIsDisplayed()
        captureReview("mothlight-sql-summary-stage4")
        onNodeWithTag("sql-query:$select").performClick()
        onNodeWithText("Сохранённые выполнения · 4").assertIsDisplayed()
        onNodeWithText("ConstraintError").assertDoesNotExist()
        captureReview("mothlight-sql-details-stage4")
        onNodeWithTag("sql-format").performClick()
        onNodeWithText(select).assertIsDisplayed()
        onNodeWithTag("sql-back").performClick()
        onNodeWithTag("sql-sort").performClick()
        onNodeWithTag("sql-sort-Errors").performClick()
        val failure = "INSERT INTO bookmarks VALUES (?, ?)"
        check(onNodeWithTag("sql-query:$failure").fetchSemanticsNode().boundsInRoot.top <
            onNodeWithTag("sql-query:$select").fetchSemanticsNode().boundsInRoot.top)
        onNodeWithTag("sql-query:$failure").performClick()
        onNodeWithText("ConstraintError").assertIsDisplayed()
        runOnIdle { state.selectTool("startup") }
        onNodeWithTag("startup-row:2").performClick().assertIsSelected()
        onNodeWithText("230 мс · последняя метка").assertIsDisplayed()
        captureReview("mothlight-startup-timeline-stage4")
        runOnIdle { state.selectTool("database") }
        onNodeWithTag("database-picker").performClick()
        onNodeWithText("products").performClick()
        onNodeWithText("8821").assertIsDisplayed()
        captureReview("mothlight-database")
        onNodeWithTag("database-schema").performClick()
        onAllNodesWithText("INTEGER")[0].assertIsDisplayed()
        captureReview("mothlight-schema")
    }

    @Test fun liveRoomDatabase() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val database = reviewRoomDatabase()
        try {
            val dao = database.reader()
            runBlocking {
                dao.seedPublications(listOf(
                    Publication("city", "Город", "Прогулки и маршруты"),
                    Publication("design", "Дизайн", "Интерфейсы и типографика"),
                    Publication("tech", "Технологии", "Мобильная разработка"),
                ))
            }
            val mothlight = reviewFixture()
            val tools = MothlightTools(listOf(databaseTool(RoomDatabaseSource(database))))
            setContent { DemoTheme(false) { MothlightPanel(mothlight, Modifier.fillMaxSize(), tools) } }
            waitUntilAtLeastOneExists(hasTestTag("database-picker"), timeoutMillis = 5000)
            onNodeWithTag("database-picker").performClick()
            onNodeWithTag("database-table-search").performTextInput("publ")
            onNodeWithText("articles").assertDoesNotExist()
            captureReview("mothlight-database-picker")
            onNodeWithText("publications").performClick()
            waitUntilAtLeastOneExists(hasText("Дизайн"), timeoutMillis = 5000)
            captureReview("mothlight-database-grid")
            onNodeWithText("Дизайн").performClick()
            onNode(hasText("Интерфейсы и типографика") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            onNodeWithText("Закрыть").performClick()
            runBlocking { dao.toggleSubscription("design") }
            waitUntilAtLeastOneExists(hasText("Изменения (1)"), timeoutMillis = 5000)
            captureReview("mothlight-database-highlight")
            onNodeWithTag("database-changes").performClick()
            onNodeWithText("subscribed · 0 → 1").assertIsDisplayed()
            captureReview("mothlight-database-changes")
            onNodeWithTag("database-schema").performClick()
            waitUntilAtLeastOneExists(hasText("Первичный ключ · позиция 1"), timeoutMillis = 5000)
            captureReview("mothlight-database-schema-stage3")
            onNodeWithTag("database-data").performClick()
            waitUntilAtLeastOneExists(hasText("Изменения (0)"), timeoutMillis = 5000)
            runBlocking { dao.seedPublications(listOf(Publication("science", "Наука", "Исследования"))) }
            waitUntilAtLeastOneExists(hasText("Изменения (1)"), timeoutMillis = 5000)
            onNodeWithTag("database-changes").performClick()
            onNodeWithText("Появилась на странице").assertIsDisplayed()
        } finally {
            database.close()
        }
    }

    @Test fun narrowDatabaseDark() = runDesktopComposeUiTest(width = 420, height = 900) {
        val database = reviewRoomDatabase()
        try {
            val dao = database.reader()
            runBlocking {
                dao.save(Bookmark(42))
                dao.save(Bookmark(51))
            }
            val tools = MothlightTools(listOf(databaseTool(RoomDatabaseSource(database))))
            val mothlight = reviewFixture()
            setContent { DemoTheme(true) { MothlightPanel(mothlight, Modifier.fillMaxSize(), tools) } }
            waitUntilAtLeastOneExists(hasTestTag("database-picker"), timeoutMillis = 5000)
            onNodeWithTag("database-picker").performClick()
            onNodeWithText("bookmarks").performClick()
            waitUntilAtLeastOneExists(hasText("51"), timeoutMillis = 5000)
            runBlocking { dao.unsave(42) }
            waitUntilAtLeastOneExists(hasText("Изменения (1)"), timeoutMillis = 5000)
            onNodeWithTag("database-changes").performClick()
            onNodeWithText("Ушла со страницы").assertIsDisplayed()
            onNodeWithText("articleId = 42").assertIsDisplayed()
            captureReview("mothlight-database-narrow-dark")
        } finally {
            database.close()
        }
    }

    @Test fun narrowSqlAndStartupDark() = runDesktopComposeUiTest(width = 420, height = 900) {
        val mothlight = reviewFixture()
        val state = MothlightPanelState().apply { selectTool("sql") }
        setContent { DemoTheme(true) { MothlightPanel(mothlight, Modifier.fillMaxSize(), state = state) } }
        captureReview("mothlight-sql-narrow-stage4")
        onNodeWithTag("sql-query:INSERT INTO bookmarks VALUES (?, ?)").performClick()
        onNodeWithText("ConstraintError").assertIsDisplayed()
        captureReview("mothlight-sql-error-stage4")
        runOnIdle { state.selectTool("startup") }
        onNodeWithTag("startup-row:4").performClick().assertIsSelected()
        captureReview("mothlight-startup-narrow-stage4")
    }

    @Test fun narrowHttpDark() = runDesktopComposeUiTest(width = 420, height = 860) {
        val mothlight = reviewFixture()
        setContent { DemoTheme(true) { MothlightPanel(mothlight, Modifier.fillMaxSize()) } }
        onNodeWithTag("http-row-3").performClick()
        onNodeWithTag("http-tab-Response").performClick()
        captureReview("mothlight-http-narrow-dark")
    }

    @Test fun narrowLogsDark() = runDesktopComposeUiTest(width = 420, height = 900) {
        val mothlight = reviewFixture()
        mothlight.logs.record(LogEntry("details", 10000, LogLevel.ERROR, "Catalog",
            "Cache update failed.\nOperation: refresh catalog\nResult: previous data retained.\nNext retry: scheduled by host."))
        val state = MothlightPanelState().apply { selectTool("logs") }
        setContent { DemoTheme(true) { MothlightPanel(mothlight, Modifier.fillMaxSize(), state = state) } }
        captureReview("mothlight-logs-narrow-dark")
        onNodeWithTag("log-row:details").performClick()
        runOnIdle { mothlight.logs.record(LogEntry("pending", 11000, LogLevel.WARN, "Network", "New entry arrived while reading")) }
        onNodeWithTag("logs-pending", useUnmergedTree = true).assertIsDisplayed()
        captureReview("mothlight-logs-expanded-dark")
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.captureReview(name: String) {
    waitForIdle()
    val output = File("build/reports/inspector-review").apply { mkdirs() }
    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(output, "$name.png"))
}

internal fun reviewFixture(): Mothlight = Mothlight(redactor = Redactor(captureJsonBodies = true)).apply {
    setEnabled(true)
    val headers = mapOf("Content-Type" to "application/json", "Authorization" to "fixture-private-token")
    val paths = listOf("/v2/products", "/v2/products/8821/reviews", "/v2/cart/items", "/v2/recommendations", "/v2/profile")
    paths.forEachIndexed { index, path ->
        val method = if (index == 2) "POST" else "GET"
        val id = network.recordStart(method, "https://api.example.com$path", headers,
            if (index == 2) CapturedBody.Text("""{"productId":8821,"quantity":2}""") else CapturedBody.NotCaptured, 1000L + index * 1000)
        when (index) {
            3 -> network.recordSuccess(id, 500, headers, """{"error":"recommendations_unavailable","retryable":true}""", 6200)
            4 -> network.recordFailure(id, "Timeout", 8400)
            else -> network.recordSuccess(id, if (index == 2) 201 else 200, headers,
                if (index == 2) """{"id":4410,"items":[{"productId":8821,"title":"Denim jacket","quantity":2}],"currency":"GBP","total":25800}"""
                else """{"items":[{"id":8821,"title":"Denim jacket","price":12900}],"page":1}""", 1142L + index * 1000)
        }
    }
    logs.record(LogEntry("1", 1000, LogLevel.INFO, "Startup", "Database opened"))
    logs.record(LogEntry("2", 2000, LogLevel.DEBUG, "Catalog", "Loaded 20 cached products"))
    logs.record(LogEntry("3", 6200, LogLevel.WARN, "Network", "Refresh failed; cached catalog remains visible"))
    logs.record(LogEntry("4", 8400, LogLevel.ERROR, "Profile", "Request timed out"))
    repeat(4) { sql.record("SELECT * FROM products WHERE category = ? ORDER BY price DESC", 2000L + it, 180L + it) }
    sql.record("UPDATE cart SET quantity = ? WHERE product_id = ?", 3000, 8)
    sql.record("INSERT INTO bookmarks VALUES (?, ?)", 3500, 4, SqlOutcome.Failed("ConstraintError"))
    startup.mark("app_created", 0)
    startup.mark("database_ready", 65)
    startup.mark("cache_loaded", 140)
    startup.mark("first_content", 230)
}

private fun reviewDatabase(): DatabaseSource = object : DatabaseSource {
    override suspend fun tables() = listOf("cart", "products", "subscriptions")
    override suspend fun page(table: String, limit: Int, offset: Int) = DatabasePage(table,
        listOf("id", "title", "price", "currency"), listOf(
            listOf("8821", "Denim jacket", "12900", "GBP").map { DatabaseCell.Text(it) },
            listOf("8822", "Canvas bag", "3900", "GBP").map { DatabaseCell.Text(it) },
            listOf("8823", "Cotton shirt", "5900", "GBP").map { DatabaseCell.Text(it) },
        ), 3, offset, limit)
    override suspend fun schema(table: String) = DatabaseSchema.Available(listOf(
        DatabaseColumn("id", "INTEGER", true, 1), DatabaseColumn("title", "TEXT", true, 0),
        DatabaseColumn("price", "INTEGER", true, 0), DatabaseColumn("currency", "TEXT", true, 0),
    ))
    override fun changes() = flowOf(Unit)
}

private fun reviewRoomDatabase() = Room.inMemoryDatabaseBuilder<DemoDatabase>()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
