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
    retention = RetentionOverlay.Visible(watcher, RetentionAction.OpenTool {
        panelState.selectTool("retention")
        // Make your panel visible here if it is currently closed.
    }),
)
```

Use `RetentionOverlay.Visible(watcher)` for a read-only badge, or the default
`RetentionOverlay.Hidden` to omit it. `RetentionOverlay` and `RetentionAction` are
in `io.github.mothlight.compose`.

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
    retention = RetentionOverlay.Visible(watcher, RetentionAction.OpenTool { panelState.selectTool("retention") }),
)
```

Factories are in `io.github.mothlight.android`; tool/state APIs are in
`io.github.mothlight.compose`. Attach these views to a lifecycle-owned layout.

The samples deliberately hold a small synthetic object until the user presses
Release. Use the recheck action afterward; there is no application/user data in
this demonstration. Remove the watcher with its host scope.

## Optional analysis of retention paths

Add `mothlight-shark` to the Android/JVM host. It uses the released
`com.squareup.leakcanary:shark:2.14` artifact from Maven Central; the portable
watcher and UI do not depend on Shark.

```kotlin
// Android: uses app-private cacheDir and Debug.dumpHprofData.
val analyzer = io.github.mothlight.shark.androidRetentionAnalyzer(context)
val tool = retentionTool(watcher, analyzer = analyzer)

// Desktop: requires a JDK (17+) with jcmd and HotSpot attach enabled.
val analyzer = io.github.mothlight.shark.jvmRetentionAnalyzer(privateCacheDirectory)
```

The Analyze button takes a snapshot only after at least one object is classified
as retained. No capture runs on tool open or during the watcher's periodic checks.
The tool offers cancellation; closing the tool cancels its analysis coroutine.
Heap capture itself can pause the VM and is not safely interruptible, so cancellation
waits for the writer to finish before removing the file. `jcmd` is invoked directly,
without a shell or reflective MXBean lookup. Paths with spaces or either kind of
quote are supported; paths containing both kinds of quotes or line breaks are
rejected before invoking the diagnostic command. The capturer can also be explicitly
injected through `HeapDumpCapture`; it must stop all writers before returning or throwing.

Reports show a GC-root category, ordered references/fields, the target class and a
Shark path signature. Selecting a watched label shows its chain. Duplicate watches
of one target receive the same path. A per-watcher session ID prevents another
watcher with the same numeric IDs from contaminating the report. Weak/soft/phantom
`Reference.referent` and Android finalizer `zombie` edges are excluded. No arbitrary
object values, thread names, exception messages, dump paths or raw dump bytes are
included in the report. Class/field names and host-provided labels remain diagnostic data.

Without a supplied analyzer, `UnsupportedRetentionAnalyzer` provides explicit
unsupported behavior. The common `RetentionAnalyzer` interface returns typed findings:

- `RetentionFinding.StrongPath` (`STRONG_PATH`): a strong path exists in this snapshot. This explains retention;
  the host still owns the decision that the object's lifetime should have ended.
- `RetentionFinding.NotInDump` (`NOT_IN_DUMP`): no matching live watched target. It may have been collected or
  its watch may have been cleared/evicted; this is not a new collection verdict.
- `RetentionFinding.NoStrongPath` (`NO_STRONG_PATH`): the target exists, but the analyzer found no strong root path.
  It is not presented as proof of collection.

`RetentionAnalysisController` serializes requests. Clear, pause and close invalidate
older results, including a result arriving after a clear. Capture/parse/size-limit/
cleanup failures remain distinct; cancellation propagates to the caller. Observation
counts continue independently and do not become historical report counts.

Each run writes to a new restricted local folder and deletes its HPROF and folder
in `finally`, including parse failures and cancellation. Raw dumps may contain all
application data. They are never uploaded or exported by Mothlight. A default
256 MiB limit is checked **after capture** and before parsing; it does not cap disk
usage during capture. Abrupt process termination can leave temporary files in the
host cache, so the host's cache-clearing policy remains relevant. A cleanup failure
is explicitly reported. JVM capture/analysis is tested with real HotSpot dumps on
Linux; Android is compiled/minified but still needs a device heap-dump smoke.
Windows/macOS desktop capture is not yet runtime-verified.

Only the watch-marker schema has stable names under R8. Host class/field names in
an obfuscated dump stay obfuscated: automatic `mapping.txt` deobfuscation is not
implemented. Consumer rules are bundled; see [minification](minification.md).

Retained byte sizes, UI grouping by signature, Android lifecycle auto-watch hooks
and native graph analysis remain future work. iOS still supports real weak-object
observation and the same common report interface. No native analyzer is supplied:
the [Kotlin/Native dump conversion experiment](leak-detection-design.md) must verify
both the retained root path and a released control before claiming support, with
Swift/Objective-C graph coverage documented separately.
