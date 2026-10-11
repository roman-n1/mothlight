# Custom Compose tabs

Any `@Composable` function can become a Mothlight tab. Use the `mothlight-compose`
module and the Compose compiler plugin in the module defining your custom UI.
There is no plugin lifecycle interface, reflection or global registry.

## Add a screen

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.mothlight.Mothlight
import io.github.mothlight.compose.MothlightPanel
import io.github.mothlight.compose.mothlightTools
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppDebugPanel(mothlight: Mothlight, cache: CacheController) {
    val tools = remember(mothlight, cache) {
        mothlightTools(mothlight) {
            tab("app-cache", title = { stringResource(AppRes.string.debug_cache) }) { modifier ->
                CacheDebugScreen(cache = cache, modifier = modifier)
            }
            tab("feature-flags", title = { stringResource(AppRes.string.debug_flags) }) { modifier ->
                FeatureFlagsScreen(modifier = modifier)
            }
        }
    }
    MothlightPanel(mothlight, tools = tools)
}
```

`CacheController`, both screens and `AppRes` are application-owned examples. Provide
localized title resources in your app. The title lambda runs in composition so it
can read the current locale. Registration does not invoke screen content.

A screen that does not accept `Modifier` also works. Wrap it to occupy the panel's
content slot (`Box` is from `androidx.compose.foundation.layout`):

```kotlin
tab("legacy-debug", title = { stringResource(AppRes.string.debug_legacy) }) { modifier ->
    Box(modifier) { ExistingDebugScreen() }
}
```

## Mix with ready-made tools

`mothlightTools(mothlight)` starts with all standard tools, then appends your entries
in declaration order. `tool(...)` accepts an existing `MothlightTool`, including
factory results such as `databaseTool(source)` and `retentionTool(watcher)`:

```kotlin
val tools = remember(mothlight, source, watcher) {
    mothlightTools(mothlight) {
        tool(databaseTool(source))
        tool(retentionTool(watcher))
        tab("app-debug", title = { stringResource(AppRes.string.debug_app) }) { modifier ->
            AppDebugScreen(modifier)
        }
    }
}
```

For a panel containing only your tools, the existing
`MothlightTools(listOf(MothlightTool(...)))` API remains available. IDs must be
nonblank and unique across the complete set. Collisions, including with standard
IDs such as `http`, are rejected when the set is built.

## State and lifetime

Keep the tools in `remember`, with captured controllers and other dependencies as
keys. Reading Compose state inside a content lambda observes its latest value;
avoid capturing a stale plain value in an unkeyed `remember` block. A replaced tool
set resets the default panel selection. Pass a remembered `MothlightPanelState`
if selection should survive rebuilding the set; `selectTool("app-cache")` opens a
custom tab programmatically. If the requested ID is absent, the first tab opens.

Only the selected content is composed. Switching away disposes it: `LaunchedEffect`
is cancelled and `DisposableEffect` cleans up. Keep subscriptions in those effects
or `collectAsState`, and keep data that must survive tab switches in an app-owned
controller. Local `remember` state is recreated when you return. Titles for all
tabs can be composed, so do not start subscriptions in title lambdas or registration.

Custom tool behavior belongs to the host. Mothlight's recording switch and Clear
button control its built-in recorders; they do not pause or reset your own tools.

## Native Android host

Pass the same tool set to `createMothlightView(context, mothlight, tools)` from
`mothlight-android`. The Activity or Fragment may still use Views. The module that
implements custom composables needs the Compose compiler plugin; a host using only
prebuilt tools does not. See [Android integration](android.md).
