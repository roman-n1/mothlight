package io.github.mothlight.compose

// Quoted values, identifiers and comments are consumed as whole tokens.
private val sqlTokens = Regex(
    """--[^\r\n]*|/\*[\s\S]*?(?:\*/|$)|'(?:''|[^'])*(?:'|$)|"(?:""|[^"])*(?:"|$)|`(?:``|[^`])*(?:`|$)|\[[^\]]*(?:\]|$)|[A-Za-z_][A-Za-z_0-9$]*|\s+|[^\s]""",
)
private val sqlClauseStarts = setOf("FROM", "WHERE", "GROUP", "HAVING", "ORDER", "LIMIT", "VALUES", "SET")

/** Display formatting changes only whitespace outside quoted text and comments. */
internal fun formatRecordedSql(sql: String): String = buildString {
    var depth = 0
    var whitespace = ""
    for (token in sqlTokens.findAll(sql).map { it.value }) {
        if (token.isNotEmpty() && token.all(Char::isWhitespace)) {
            whitespace = token
            continue
        }
        if (whitespace.isNotEmpty()) {
            append(if (depth == 0 && token.uppercase() in sqlClauseStarts) "\n" else whitespace)
            whitespace = ""
        }
        append(token)
        when (token) {
            "(" -> depth++
            ")" -> depth = (depth - 1).coerceAtLeast(0)
        }
    }
    append(whitespace)
}
