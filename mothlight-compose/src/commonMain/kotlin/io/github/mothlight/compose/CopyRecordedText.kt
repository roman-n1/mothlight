package io.github.mothlight.compose

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import io.github.mothlight.compose.resources.*
import org.jetbrains.compose.resources.stringResource

/** Shared feedback for copying data that has already passed through its recorder. */
@Composable
internal fun CopyRecordedText(label: String, value: String, tag: String, enabled: Boolean = true) {
    CopyRecordedText(label, tag, enabled, value) { value }
}

/** Build larger exports only when the user actually requests a copy. */
@Composable
internal fun CopyRecordedText(label: String, tag: String, enabled: Boolean, contentKey: Any, value: () -> String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(contentKey) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1500); copied = false } }
    TextButton(onClick = { clipboard.setText(AnnotatedString(value())); copied = true }, enabled = enabled, modifier = Modifier.testTag(tag)) {
        Text(if (copied) stringResource(Res.string.http_copied) else label)
    }
}
