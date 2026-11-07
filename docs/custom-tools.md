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
factory results such as `databaseTool(source)`:

```kotlin
val tools = remember(mothlight, source) {
    mothlightTools(mothlight) {
        tool(databaseTool(source))
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

## Tabs with subtools

Use `group` to place multiple host tools under one tab. Groups start empty;
the standard tools remain at the panel level. Each group must contain at least one
tool. Child IDs must be nonblank and unique within their group, so independent
integrations can use the same child IDs without collisions.

```kotlin
val tools = remember(mothlight, databaseSource, socketInspector, componentInspector) {
    mothlightTools(mothlight) {
        tool(databaseTool(databaseSource))
        group("messenger", title = { stringResource(AppRes.string.debug_messenger) }) {
            tab("sockets", title = { stringResource(AppRes.string.debug_sockets) }) { modifier ->
                SocketDebugScreen(socketInspector, modifier)
            }
            tab("components", title = { stringResource(AppRes.string.debug_components) }) { modifier ->
                ComponentDebugScreen(componentInspector, modifier)
            }
        }
    }
}
```

The screens, inspectors and resources above belong to the host. Centrifuge
connections and Decompose trees stay there; neither is a Mothlight dependency.
The grouped-tool tests exercise selection, disposal and inheritance of the host theme.

For an integration that already returns `MothlightTools`, use
`tool(toolGroup("integration", { localizedTitle() }, integrationTools))`. Import
`io.github.mothlight.compose.toolGroup`. It also accepts a host-owned
`MothlightPanelState` to select a child programmatically. Selection is retained
while the group tool object lives; subscriptions in child composition effects
are disposed when switching children or leaving the group. Rebuilding the group
creates fresh selection unless the host supplies the same state. An absent
selected child falls back to the first available child.

## Actron integration boundary

Actron's existing `HistoryPanel` and `InspectorScreen` are Compose screens. The
host can supply these screens to a group without adding an Actron dependency to
Mothlight, or a Mothlight dependency to Actron. For example, this host-owned helper
accepts the actual screens as composition callbacks:

```kotlin
fun MothlightToolsBuilder.actronTools(
    title: @Composable () -> String,
    historyTitle: @Composable () -> String,
    inspectorTitle: @Composable () -> String,
    history: @Composable (Modifier) -> Unit,
    inspector: @Composable (Modifier) -> Unit,
) {
    group("actron", title) {
        tab("history", historyTitle, history)
        tab("inspector", inspectorTitle, inspector)
    }
}
```

Wrap an existing screen without a modifier in `Box(modifier)`, as above. Create
Actron's timeline, recording session and inspector state in the application graph
or a composition scope above the selected child. This keeps recording and history
alive when the tab is hidden. UI effects may be scoped to the selected child.
Replay and continuation actions remain the host's explicit callbacks. The
Mothlight Record/Clear controls do not control Actron sessions.

This is an integration recipe, not a bundled Actron adapter. Automated tests
exercise grouped Compose screens and disposal; they do not run Actron replay.

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
