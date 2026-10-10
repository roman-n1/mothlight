package io.github.mothlight

/** Absence and omission reasons are data, never magic strings or missing references. */
sealed interface CapturedBody {
    data object NotCaptured : CapturedBody
    data object TooLarge : CapturedBody
    data object NotJson : CapturedBody
    data class Text(val value: String) : CapturedBody
}

sealed interface ScreenContext {
    data object Unspecified : ScreenContext
    data class Named(val value: String) : ScreenContext
}

sealed interface SqlOutcome {
    data object Succeeded : SqlOutcome
    data class Failed(val category: String) : SqlOutcome
}

/** SQL NULL and BLOB are distinct from text containing their display labels. */
sealed interface DatabaseCell {
    data object SqlNull : DatabaseCell
    data object Blob : DatabaseCell
    data class Text(val value: String) : DatabaseCell
}
