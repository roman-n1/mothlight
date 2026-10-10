package io.github.mothlight



/** Render recorded, redacted data as a POSIX cURL command. Masked credentials need manual replacement. */
fun buildCurlCommand(transaction: NetworkTransaction): String = buildString {
    append("curl -X ").append(transaction.method)
    transaction.requestHeaders
        .filterValues { it != MASKED_HEADER_VALUE }
        .forEach { (name, value) ->
            append(" \\\n  -H ").append(shellQuote("$name: $value"))
        }
    if (transaction.requestHeaders.any { it.value == MASKED_HEADER_VALUE }) {
        append(" \\\n  -H ").append(shellQuote("Authorization: Bearer <PASTE_TOKEN>"))
    }
    transaction.requestBody?.takeIf { it.isNotEmpty() }?.let { body ->
        append(" \\\n  -d ").append(shellQuote(body))
    }
    append(" \\\n  ").append(shellQuote(transaction.url))
}


// Preserve literal single quotes inside POSIX single-quoted arguments.
private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private const val MASKED_HEADER_VALUE = "***"
