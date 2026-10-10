# Memory usage overlay

The panel's **Show memory usage** switch belongs to `mothlight.memoryOverlay`.
It starts off, is independent of event recording, and is not reset by `clear()`.
Each Mothlight instance owns its setting. No global registry or architecture
framework is involved.

Place one overlay at the application boundary so it remains visible after the
panel closes. The panel exposes the setting; it does not insert a system window.

```kotlin
Box(Modifier.fillMaxSize()) {
    AppContent()
    MemoryUsageOverlay(mothlight, Modifier.align(Alignment.TopEnd))
}
```

Import `io.github.mothlight.compose.MemoryUsageOverlay`. The host controls the
position and when this composable is present. Remove it while its screen is
inactive. Polling starts immediately when enabled, repeats every second, and
stops when disabled or removed from composition. No history is retained and no
application scope is created. Reading runs on an injectable worker dispatcher.

For a native Android Views application, add
`createMemoryOverlayView(context, mothlight)` to the host layout, for example
as the last child of a `FrameLayout` with top/end gravity and wrap-content size.
Import it from `io.github.mothlight.android`. The view needs a view-tree lifecycle
owner just like `createMothlightView`. It disposes its composition on detach;
remove it while the host screen is inactive. No Compose source/compiler plugin
is required in the consuming application.

## Persistence

The default setting lives only for the Mothlight instance's lifetime. To restore
it across launches, pass a `MemoryOverlayStore` to the constructor:

```kotlin
val mothlight = Mothlight(memoryOverlayStore = object : MemoryOverlayStore {
    override fun read() = preferences.getBoolean("mothlight.memory", false)
    override fun write(enabled: Boolean) {
        preferences.edit().putBoolean("mothlight.memory", enabled).apply()
    }
})
```

The example uses Android SharedPreferences. Other platforms can supply their own
store. Store access is synchronous; use fast cached preferences and own any
asynchronous persistence in the host. Programmatic control uses
`mothlight.memoryOverlay.setEnabled(true)`.

## What the numbers mean

| Platform | Available metrics |
| --- | --- |
| Android | ART heap used/limit; allocated native heap |
| JVM | Managed heap used/limit; JVM non-heap (metaspace/code cache, not all native allocations) |
| JVM on Linux | Additionally current-process resident memory from `/proc/self/status` |
| JVM on Windows | Heap and non-heap; process working set requires a host adapter |
| JVM on macOS | Heap and non-heap; no built-in process footprint reader |
| iOS | Current-process physical footprint via Mach; no invented JVM heap or limit |
| JS / Wasm | Explicit unavailable message; no portable process-memory API used |

Heap, native/non-heap and process metrics overlap or measure different things.
They are displayed separately and **must not be added**. Child/helper processes
are excluded. Heap limits are runtime limits, not the machine's RAM. Native
allocations are not constrained by that heap limit.

Values use binary MiB/GiB. The background changes strictly above 60% and 80% of
the heap limit. These are visual heap-usage thresholds, not OS memory-pressure
notifications or leak detection. Unknown metrics stay absent; a failed read
clears the previous sample and a later tick can recover. Cancellation propagates.

The source (`MemorySource`) and dispatcher can be supplied to the Compose overlay.
Headless consumers may use the `mothlight-memory` module and collect
`observeMemory(settings, source)` with their own scope. Each collector owns its
polling; mount one overlay per host window to avoid duplicate reads.

Pass `retention = RetentionOverlay.Visible(watcher, RetentionAction.OpenTool { /* open tool */ })` to add a live
[retained-object badge and tool](retention.md). The tool supports an explicit GC
request; ordinary memory sampling never forces GC. Android/JVM heap analysis is
available through the separate Shark adapter. See the [analysis proposal](leak-detection-design.md)
for the remaining native analysis work. The original application's own settings are unchanged.

## Platform dependencies

The module has no JNA dependency or reflective native binding. Android calls are
direct. The iOS Mach reader is built through cinterop; it calls a system API and
ships no third-party native binary. On JVM 17, Windows process working set is
unavailable without a native host adapter; virtual committed memory is never
substituted for physical resident memory. JVM heap and non-heap still work.

A host can use the existing typed `MemorySource` interface to combine standard
JVM metrics with a native process reading:

```kotlin
fun withNativeResidentReader(readResidentBytes: () -> MemoryReading): MemorySource = MemorySource {
    PlatformMemorySource.read().copy(
        processBytes = readResidentBytes(),
        processKind = ProcessMemoryKind.RESIDENT,
    )
}
```

`MemoryReading.Bytes(value)` represents a non-negative measurement, including zero.
Return `MemoryReading.Unavailable` when the host cannot measure a value.
`MemoryReading.bytes(rawValue)` normalizes negative platform readings to unavailable.

Pass that source to `MemoryUsageOverlay` or `createMemoryOverlayView`. The callback
must use the host's typed native bridge; Mothlight does not load a DLL or start a
shell process on every sample. See [minification](minification.md) for packaged
consumer rules and their verification.
