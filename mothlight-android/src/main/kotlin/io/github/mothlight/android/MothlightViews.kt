@file:JvmName("MothlightViews")
package io.github.mothlight.android

import android.content.Context
import androidx.compose.material3.MaterialTheme
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
fun createMothlightView(context: Context, mothlight: Mothlight, tools: MothlightTools = defaultMothlightTools(mothlight)): ComposeView =
    ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { MaterialTheme { MothlightPanel(mothlight, tools = tools) } }
    }
