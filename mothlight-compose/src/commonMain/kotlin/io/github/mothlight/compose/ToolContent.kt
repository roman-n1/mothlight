package io.github.mothlight.compose

import androidx.compose.runtime.*
import io.github.mothlight.*
import io.github.mothlight.compose.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun localizedBody(body: CapturedBody): String = when (body) {
    CapturedBody.NotCaptured -> stringResource(Res.string.body_not_captured)
    CapturedBody.TooLarge -> stringResource(Res.string.body_too_large)
    CapturedBody.NotJson -> stringResource(Res.string.body_not_json)
    is CapturedBody.Text -> body.value
}
