package io.github.mothlight.sample

import kotlinx.serialization.json.*
import java.net.URI

internal data class ArticleFeed(val publication: Publication, val tag: String)
// Keep the original IDs so existing subscription choices survive the switch to live feeds.
internal val articleFeeds = listOf(
    ArticleFeed(Publication("design", "DEV · Design", "Дизайн интерфейсов и пользовательский опыт", true), "design"),
    ArticleFeed(Publication("technology", "DEV · Kotlin", "Kotlin, Android и мультиплатформенная разработка", true), "kotlin"),
    ArticleFeed(Publication("city", "DEV · Web", "Веб-разработка и технологии браузера"), "webdev"),
)
internal val publications = articleFeeds.map { it.publication }

internal fun decodeArticles(body: String, publicationId: String): List<Article> = Json.parseToJsonElement(body).jsonArray.map { item ->
    val row = item.jsonObject
    val url = row.getValue("url").jsonPrimitive.content
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host == "dev.to" && uri.userInfo == null) { "Unexpected article URL" }
    val summary = row["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
    Article(
        id = row.getValue("id").jsonPrimitive.long,
        publicationId = publicationId,
        title = row.getValue("title").jsonPrimitive.content,
        summary = summary,
        body = summary,
        minutes = row["reading_time_minutes"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(1) ?: 1,
        sourceUrl = url,
        author = row["user"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}
