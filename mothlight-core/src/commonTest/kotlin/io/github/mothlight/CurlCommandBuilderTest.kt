package io.github.mothlight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** cURL-экспорт HTTP-инспектора: чистый билдер (Chucker-паритет «copy as cURL»). */
class CurlCommandBuilderTest {

    @Test
    fun get_withPlainHeaders_buildsCurl() {
        val curl = buildCurlCommand(
            transaction(method = "GET", headers = mapOf("Accept" to "application/json"), body = null),
        )

        assertTrue(curl.startsWith("curl -X GET"))
        assertTrue("-H 'Accept: application/json'" in curl)
        assertTrue("'https://api.test/v1/chats'" in curl)
        assertFalse("-d" in curl, "GET без тела — без -d")
    }

    @Test
    fun maskedAuthorization_isReplacedWithPlaceholder_notLeaked() {
        val curl = buildCurlCommand(
            transaction(method = "GET", headers = mapOf("Authorization" to "***"), body = null),
        )

        assertFalse("Authorization: ***" in curl, "замаскированный заголовок бесполезен — не включаем")
        assertTrue("Authorization: Bearer <PASTE_TOKEN>" in curl, "напоминание подставить живой токен")
    }

    @Test
    fun postBody_isQuoted_singleQuotesEscaped() {
        val curl = buildCurlCommand(
            transaction(method = "POST", headers = emptyMap(), body = """{"text":"it's fine"}"""),
        )

        assertTrue("-d '" in curl)
        assertTrue("""it'\''s fine""" in curl, "одинарная кавычка экранирована по-POSIX")
    }

    private fun transaction(method: String, headers: Map<String, String>, body: String?) =
        NetworkTransaction(
            id = "1",
            sequenceNumber = 1,
            method = method,
            url = "https://api.test/v1/chats",
            requestHeaders = headers,
            requestBody = body?.let { CapturedBody.Text(it) } ?: CapturedBody.NotCaptured,
            responseHeaders = emptyMap(),
            responseBody = CapturedBody.NotCaptured,
            startedAtMillis = 0,
            status = Status.Completed(200, 1),
        )
}
