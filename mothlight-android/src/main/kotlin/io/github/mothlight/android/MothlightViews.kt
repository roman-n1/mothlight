@file:JvmName("MothlightViews")
package io.github.mothlight.android

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.mothlight.Mothlight
import io.github.mothlight.compose.*

/**
 * Add the returned View to an Activity/Fragment layout with ViewTreeLifecycleOwner.
 * The host does not need Compose source code or the Compose compiler plugin.
 * Dispose the composition with the host view lifecycle, including Fragment view recreation.
 */
@JvmOverloads
fun createMothlightView(
    context: Context,
    mothlight: Mothlight,
    tools: MothlightTools = defaultMothlightTools(mothlight),
    state: MothlightPanelState = MothlightPanelState(),
): ComposeView = createMothlightView(context, mothlight, tools, state) { content ->
    DefaultMothlightTheme(content)
}

/** Use a Compose theme when the host uses the Compose compiler. */
fun createMothlightView(
    context: Context,
    mothlight: Mothlight,
    tools: MothlightTools = defaultMothlightTools(mothlight),
    state: MothlightPanelState = MothlightPanelState(),
    theme: @Composable (@Composable () -> Unit) -> Unit,
): ComposeView =
    ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { theme { MothlightPanel(mothlight, tools = tools, state = state) } }
    }

/** Attach at the host boundary. Detaching this view stops memory sampling. */
@JvmOverloads
fun createMemoryOverlayView(
    context: Context,
    mothlight: Mothlight,
    source: io.github.mothlight.memory.MemorySource = io.github.mothlight.memory.PlatformMemorySource,
): ComposeView = createMemoryOverlayView(context, mothlight, source) { content ->
    DefaultMothlightTheme(content)
}

/** Use a Compose theme when the host uses the Compose compiler. */
fun createMemoryOverlayView(
    context: Context,
    mothlight: Mothlight,
    source: io.github.mothlight.memory.MemorySource = io.github.mothlight.memory.PlatformMemorySource,
    theme: @Composable (@Composable () -> Unit) -> Unit,
): ComposeView = ComposeView(context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    setContent { theme { MemoryUsageOverlay(mothlight, source = source) } }
}

@Composable
private fun DefaultMothlightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}
