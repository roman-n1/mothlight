package io.github.mothlight.sample

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.mothlight.*
import io.github.mothlight.ktor.installMothlight
import io.github.mothlight.room3.RoomDatabaseSource
import io.github.mothlight.sqlite.TracingSQLiteDriver
import io.ktor.client.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Public DEV API over HTTPS; injectable transport keeps automated tests offline. */
class DemoSession(databaseFile: File = defaultDatabaseFile(), engine: HttpClientEngine = CIO.create()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val mothlight = Mothlight(redactor = Redactor(captureJsonBodies = true, bodyLimit = 128 * 1024)).apply { setEnabled(true) }
    private val database = Room.databaseBuilder<DemoDatabase>(databaseFile.absolutePath)
        .setDriver(TracingSQLiteDriver(BundledSQLiteDriver(), mothlight.sql, { mothlight.enabled.value }))
        .addMigrations(readerMigration1To2, readerMigration2To3)
        .setQueryCoroutineContext(Dispatchers.IO).build()
    val databaseSource = RoomDatabaseSource(database)
    private val dao = database.reader()
    val articles = dao.articles().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val publications = dao.publications().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val bookmarks = dao.bookmarks().stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow(false)
    val error = mutableError.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val lock = Mutex()
    private val sequence = AtomicLong()
    private val client = HttpClient(engine) {
        installMothlight(mothlight.network)
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
    }
    private val transport = engine


    init { mothlight.startup.mark("session_created", now()) }

    suspend fun initialize() {
        perform {
            dao.seedPublications(io.github.mothlight.sample.publications)
            io.github.mothlight.sample.publications.forEach { dao.updatePublication(it.id, it.title, it.description) }
            mothlight.startup.mark("database_ready", now())
            mutableReady.value = true
        }
        if (ready.value) refresh()
    }

    suspend fun refresh() = perform {
        // Sequential requests avoid bursts against the public API. Commit only a complete refresh.
        val incoming = articleFeeds.flatMap { feed ->
            val response = client.get("https://dev.to/api/articles") {
                parameter("tag", feed.tag)
                parameter("per_page", 10)
                header(HttpHeaders.Accept, "application/vnd.forem.api-v1+json")
                header(HttpHeaders.UserAgent, "Mothlight-Reader-Demo/0.1")
            }
            check(response.status == HttpStatusCode.OK) { "Catalog HTTP ${response.status.value}" }
            decodeArticles(response.bodyAsText(), feed.publication.id)
        }
        // One DEV article may occur in several tag feeds; retain a stable single owner.
        val unique = incoming.distinctBy { it.id }
        dao.replaceCatalog(unique)
        log("DEV catalog stored: ${unique.size} articles")
    }

    fun articleContent(id: Long): Flow<ArticleContent?> = dao.articleContent(id)

    /** Refresh on every open; a failed request leaves the independently cached full text intact. */
    suspend fun loadArticle(id: Long): Boolean {
        try {
            val response = client.get("https://dev.to/api/articles/$id") {
                header(HttpHeaders.Accept, "application/vnd.forem.api-v1+json")
                header(HttpHeaders.UserAgent, "Mothlight-Reader-Demo/0.1")
            }
            check(response.status == HttpStatusCode.OK) { "Article HTTP ${response.status.value}" }
            val text = withContext(Dispatchers.Default) { articleText(response.bodyAsText(), id) }
            dao.storeContent(ArticleContent(id, text))
            log("Full article stored: $id")
            return true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            mothlight.logs.record(LogEntry("log-${sequence.incrementAndGet()}", now(), LogLevel.ERROR,
                "Reader", "Article load failed: ${failure.javaClass.simpleName}"))
            return false
        }
    }

    suspend fun toggleSubscription(id: String) = perform {
        dao.toggleSubscription(id)
        log("Publication subscription changed")
    }
    suspend fun toggleBookmark(id: Long) = perform {
        dao.toggleBookmark(id)
        log("Article bookmark changed")
    }

    private suspend fun perform(action: suspend () -> Unit) = lock.withLock {
        mutableBusy.value = true
        mutableError.value = false
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            mutableError.value = true
            mothlight.logs.record(LogEntry("log-${sequence.incrementAndGet()}", now(), LogLevel.ERROR,
                "Reader", "Operation failed: ${failure.javaClass.simpleName}"))
        } finally { mutableBusy.value = false }
    }

    fun firstFrame() { mothlight.startup.mark("first_frame", now()) }
    private fun log(message: String) {
        val id = sequence.incrementAndGet()
        mothlight.logs.record(LogEntry("log-$id", now(), LogLevel.INFO, "Reader", message))
    }
    fun close() { scope.cancel(); client.close(); transport.close(); database.close() }
    private fun now() = System.currentTimeMillis()
}

private fun defaultDatabaseFile(): File {
    val directory = File(System.getProperty("user.home"), ".mothlight-demo")
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create demo storage" }
    return File(directory, "reader.db")
}
