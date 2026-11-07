package io.github.mothlight.compose

import io.github.mothlight.StartupSpan

internal data class StartupRow(
    val id: Long,
    val stage: String,
    val offsetMillis: String,
    val durationMillis: String,
    val startFraction: Float,
    val endFraction: Float,
    val isLast: Boolean,
)

internal fun startupRows(spans: List<StartupSpan>): List<StartupRow> {
    if (spans.isEmpty()) return emptyList()
    val origin = spans.first().startedAtMillis.toULong()
    val total = spans.last().startedAtMillis.toULong() - origin
    fun fraction(offset: ULong): Float =
        if (total == 0uL) 0f else (offset.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    return spans.mapIndexed { index, span ->
        val next = spans.getOrElse(index + 1) { span }
        val offset = span.startedAtMillis.toULong() - origin
        val end = next.startedAtMillis.toULong() - origin
        StartupRow(span.id, span.stage, offset.toString(), (end - offset).toString(),
            fraction(offset), fraction(end), index == spans.lastIndex)
    }
}
