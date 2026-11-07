package io.github.mothlight.sample

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.serialization.json.*

internal val catalog = listOf(
    Article(1, "design", "Меньше шума, больше смысла", "Почему хороший интерфейс начинается с того, что мы убираем.", "У каждого экрана есть главное действие. Начните с него: уберите конкурирующие кнопки и дайте содержимому пространство.\n\nПроверьте, понятен ли экран без инструкции. Подписи должны описывать результат действия, а состояния загрузки — объяснять, что происходит.\n\nПростота не означает отсутствие возможностей. Дополнительные инструменты можно открывать тогда, когда они понадобились.", 4),
    Article(2, "technology", "Приложение, которое работает без сети", "Локальная база как основа спокойного пользовательского опыта.", "Сохраняйте полученные данные локально и показывайте их сразу при следующем запуске. Сеть обновляет базу, а интерфейс наблюдает за ней.\n\nПодписки и закладки принадлежат пользователю. Обновление каталога не должно их стирать.\n\nКогда запрос завершается ошибкой, оставьте доступным сохранённое содержимое и предложите повторить попытку.", 6),
    Article(3, "city", "Выходной без маршрута", "Как заново открыть знакомый район за одну прогулку.", "Выберите улицу, на которой давно не были. Уберите телефон в карман и обратите внимание на детали: вывески, деревья, старые фасады.\n\nЗайдите в небольшую кофейню и запишите три места, куда захотелось вернуться. Иногда лучший маршрут складывается сам.", 3),
    Article(4, "design", "Цвет как часть навигации", "Одна палитра объединяет продукт и его инструменты.", "Цвет помогает узнавать действия и понимать состояние. Используйте один акцент для главного действия и спокойные поверхности для чтения.\n\nВстроенные инструменты тоже часть приложения. Общие цвета, шрифты и формы помогают сохранить контекст при переходе к ним.", 5),
    Article(5, "technology", "Что происходит после нажатия кнопки", "От запроса до записи в базу: маленькое путешествие данных.", "Нажатие запускает запрос. Ответ проходит проверку, затем сохраняется в локальную базу. Наблюдатель замечает изменение и обновляет экран.\n\nЧтобы понять каждый шаг, полезно видеть запрос, SQL и событие в одном месте. Диагностика становится понятнее, когда связана с настоящими действиями.", 7),
    Article(6, "city", "Места для медленного чтения", "Три идеи для часа наедине с хорошим текстом.", "Библиотека, скамейка в тихом парке или столик у окна — выберите место, где не нужно спешить.\n\nСохраните статью заранее. Отключите уведомления и оставьте себе время дочитать её до конца.", 3),
)

internal fun catalogJson(publicationId: String): String = buildJsonArray {
    catalog.filter { it.publicationId == publicationId }.forEach { article -> add(buildJsonObject {
        put("id", article.id); put("title", article.title); put("description", article.summary)
        put("url", "https://dev.to/fixture/article-${article.id}")
        put("reading_time_minutes", article.minutes)
        put("user", buildJsonObject { put("name", "Test Author") })
    }) }
}.toString()

internal fun fixtureEngine() = MockEngine { request ->
    if (request.url.encodedPath.endsWith("/0")) {
        respond("{\"error\":\"not found\"}", HttpStatusCode.NotFound, headersOf("Content-Type", "application/json"))
    } else if (request.url.encodedPath.startsWith("/api/articles/")) {
        val id = request.url.encodedPath.substringAfterLast('/').toLong()
        respond(buildJsonObject {
            put("id", id)
            put("body_html", "<h2>Full article</h2><p>This is the complete article text, beyond the feed summary.</p><pre>val answer = 42</pre>")
        }.toString(), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
    } else {
        val feed = articleFeeds.single { it.tag == request.url.parameters["tag"] }
        respond(catalogJson(feed.publication.id), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
    }
}
