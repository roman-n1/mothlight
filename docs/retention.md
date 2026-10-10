# Retained objects

`mothlight-retention` observes objects after the host says their useful lifetime
has ended. It is independent of Compose, Mothlight event recording, navigation
frameworks and DI containers. Its watcher starts disabled and belongs to a host
coroutine scope. Closing the panel does not stop it.

```kotlin
val watcher = RetentionWatcher(applicationDebugScope)
watcher.setEnabled(true)

// At the lifetime boundary your application owns:
watcher.expectReleased(screenModel, label = "Details model", reason = "Screen closed")
```

Imports: `io.github.mothlight.retention.RetentionWatcher`. Labels and reasons are
host-supplied safe metadata (maximum 256 and 512 characters); never pass object
`toString()` values, user identifiers or request payloads. Targets are held weakly
and are never exposed through snapshots.

## One interface, explicit platform capabilities

| Platform | Observation |
| --- | --- |
| Android / JVM | Java weak reference and reference queue; no reflective object traversal |
| iOS | Kotlin/Native weak reference; observes Kotlin objects, not the complete Swift/Objective-C graph |
| JS / Wasm | Explicitly unsupported; never pretends that a target was collected |

The portable states are `WATCHING`, `RETAINED`, and `COLLECTED`. By default the
watcher checks once per second and marks surviving objects after five seconds.
**Retained is an observation, not a confirmed leak.** A delayed collection or a
legitimate owner can keep the object alive. The live retained count decreases
when later checks observe collection. Collected entries remain as bounded history.

`checkNow()` performs a passive check. `checkNow(requestCollection = true)` asks
for one GC cycle for the whole batch, on an injectable worker dispatcher. JVM/ART
GC is best effort; the result says a collection was requested, not guaranteed.
Native `GC.collect()` waits for its collection. Routine background observation
never forces GC. Neither action creates a heap dump.

A failed backend check is shown explicitly and preserves the previous
observations. Cancellation propagates. A manual check is independent of the
background loop and checks are serialized, so repeated clicks cannot run multiple
GC requests concurrently.

## Panel and badge

```kotlin
val panelState = remember { MothlightPanelState() }
val tools = remember(mothlight, watcher) {
    MothlightTools(defaultMothlightTools(mothlight).entries + retentionTool(watcher))
}
MothlightPanel(mothlight, tools = tools, state = panelState)
MemoryUsageOverlay(
    mothlight,
    retentionWatcher = watcher,
    onOpenRetention = {
        panelState.selectTool("retention")
        // Make your panel visible here if it is currently closed.
    },
)
```

The memory switch controls badge visibility, not the watcher's lifetime. The
retention tool shows the platform capability, current count, check outcome,
bounded entries, release reasons, age and filtering. The same UI is available
through Android View factories; pass the watcher and the host-owned panel state.
All library controls and messages use English/Russian resources. Host labels are
data and are not translated.

## Ownership and limits

- `setEnabled(false)` clears tracked entries and stops observation. Enabling again
  starts a new observation session; it cannot reconstruct missed lifetimes.
- `clear()` drops entries and eviction statistics but preserves enabled state.
  IDs are never reused. An in-flight check cannot restore cleared entries.
- `close()` is terminal and cancels only the watcher's worker. Cancelling the host
  scope closes the watcher too. Late registration is rejected explicitly.
- Capacity defaults to 200 entries, including collected history. Oldest entries
  are evicted; the UI shows an eviction count. Counts describe the retained window,
  not all objects in the application. No unbounded per-object jobs are started.
- `snapshots` is a cold projection of current state; `snapshot` provides the latest
  value without a UI collector. The watcher itself runs in the supplied host scope.

For a native Android host (no Compose functions needed):

```kotlin
val panelState = MothlightPanelState()
val tools = MothlightTools(defaultMothlightTools(mothlight).entries + retentionTool(watcher))
val panel = createMothlightView(context, mothlight, tools, panelState)
val badge = createMemoryOverlayView(context, mothlight,
    retentionWatcher = watcher,
    onOpenRetention = { panelState.selectTool("retention") },
)
```

Factories are in `io.github.mothlight.android`; tool/state APIs are in
`io.github.mothlight.compose`. Attach these views to a lifecycle-owned layout.

The samples deliberately hold a small synthetic object until the user presses
Release. Use the recheck action afterward; there is no application/user data in
this demonstration. Remove the watcher with its host scope.

## Analysis remains a separate layer

This module does not yet compute paths to GC roots, retained heap sizes or leak
cause signatures. The [research/design](leak-detection-design.md) describes Shark
integration for Android/JVM and a Kotlin/Native dump conversion experiment.
The common observation UI does not imply those analyzers already work on iOS.
