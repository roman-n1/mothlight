# Leak detection: research and proposed direction

Research date: 2026-10-10. [Portable observation, the retention tool and badge](retention.md)
are implemented. Heap analysis and native dump conversion below remain proposals;
a retained object is not a confirmed leak.

## What to reuse

LeakCanary separates reachability tracking, heap capture, analysis and presentation.
A watched object is expected to become unreachable after an explicit lifecycle
boundary. A surviving weak reference is a retained-object signal. A heap analysis
then finds reference paths and groups reports by a cause signature. These are
separate levels of evidence, not interchangeable counters.
[How LeakCanary works](https://square.github.io/leakcanary/fundamentals-how-leakcanary-works/).

The current tracker uses keyed weak references and a ReferenceQueue, avoiding
strong references produced by inspecting the target. It removes collected entries
and exposes current retained counts. Its clock is injectable. Reuse these ideas
in the JVM/Android adapter; do not copy Java classes into commonMain.
[Tracker source](https://github.com/square/leakcanary/blob/main/object-watcher/object-watcher/src/main/java/leakcanary/ReferenceQueueRetainedObjectTracker.kt).

Shark is a separately usable Kotlin/JVM analyzer for Android and Java HPROF files.
Its layers cover parsing, graph navigation, analysis and Android heuristics.
Use its released artifacts for a JVM analysis adapter instead of maintaining a
second parser. Android-specific inspectors belong only to the Android adapter.
[Shark architecture](https://square.github.io/leakcanary/shark/).

The latest GitHub release inspected was `v3.0-alpha-9`; the source links above
point at main and are not a promise that those exact APIs exist in a stable
artifact. Pin and validate the chosen released version before integration.
[Release](https://github.com/square/leakcanary/releases/tag/v3.0-alpha-9).

## Other tools and the platform boundary

| Tool | Useful capability | Role for Mothlight |
| --- | --- | --- |
| [Eclipse MAT](https://help.eclipse.org/latest/topic/org.eclipse.mat.ui.help/gettingstarted/basictutorial.html) | Dominator tree, retained sizes, paths to GC roots, leak-suspect reports | Reference workflow and external desktop analysis/export target |
| [VisualVM](https://visualvm.github.io/) | Live JVM monitoring and heap investigation | Complementary desktop investigation; not a commonMain runtime dependency |
| [KOOM](https://github.com/KwaiAppTeam/KOOM) | Android Java/native/thread memory monitoring | Study batching and runtime cost; not a Kotlin/Native detector |
| [FBRetainCycleDetector](https://github.com/facebook/FBRetainCycleDetector) | Runtime Objective-C retain-cycle inspection | Reference for an optional Apple adapter, not a Kotlin heap graph implementation |
| [Kotlin/Native memory tools](https://kotlinlang.org/docs/native-memory-manager.html) | GC metrics and integration with Instruments | Native observations and performance diagnostics |

Kotlin/Native already exposes `Debugging.dumpMemory(fd)` and has a JVM tool named
`kdumputil` that converts its dump to HPROF. The converter is present in the
Kotlin 2.4.10 source used by Mothlight. This makes an offline desktop analysis
path plausible; it does not establish that Shark can identify our tracked
objects or recover complete native/Objective-C ownership chains.
[dumpMemory API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.native.runtime/-debugging/dump-memory.html),
[converter entry point](https://github.com/JetBrains/kotlin/blob/v2.4.10/kotlin-native/tools/kdumputil/src/main.kt).

Native GC and Swift/Objective-C ARC have different lifetime rules, and interop
objects can be reclaimed across multiple GC cycles. A JVM grace period cannot
be treated as universal proof of a native leak. Kotlin-only dumps must not be
presented as a complete process-memory graph.
[ARC integration](https://kotlinlang.org/docs/native-arc-integration.html).

The reviewed tools did not establish a ready-made equivalent with one supported
Android/JVM/iOS/Web in-app workflow. This is a limit of the investigation, not
proof that no other project exists. LeakCanary's KMP integration request is
itself specifically about Android test targets, not native heap analysis.
[KMP issue](https://github.com/square/leakcanary/issues/2821).

## Gaps in the original overlay's detector

The original watcher is a useful starting point: it receives an object after its
expected lifetime, stores a weak reference, waits, requests GC and reports a tag.
It has JVM/Android and Kotlin/Native implementations.

Extraction should change these behaviors:

- Replace the global installed watcher and global tag counters with an explicitly
  owned session, scope and immutable bounded snapshots.
- Separate **currently retained**, **collected later**, and historical reports.
  The old increment-only tag counter cannot represent currently live leaks.
- Batch checks and GC requests. One coroutine and multiple GC requests per object
  can amplify work during mass teardown.
- Report unsupported platforms explicitly. A web stub returning null must not
  look like a successfully collected object.
- Distinguish retained candidates from analyzed reference paths. Repeated GC
  requests and a timeout alone do not explain why an object remains reachable.

## Proposed Mothlight modules and experience

1. **Portable watcher**: host calls `expectReleased(target, label, reason)` at the
   lifetime boundary it owns. Use weak handles, monotonic time, bounded tracking,
   cancellation and independent sessions. No Decompose, DI or navigation dependency.
2. **Retention tool**: current retained count in the memory badge; a click opens
   the tool with labels, observation age, expected-release reason and collected
   history. Show explicit capabilities and evidence states. Do not label a
   candidate as a proven leak. Keep the live count distinct from report totals.
3. **Android/JVM analysis adapter**: opt-in heap capture plus Shark, reference-chain
   viewer, cause grouping and retained-size results when the analyzer provides
   them. Optional Android lifecycle hooks live in this adapter.
4. **Native adapter**: weak-reference observation first. A separate feasibility
   test captures a deliberately retained Kotlin object, converts its dump and
   verifies its ID, root path and collected control case. Only then promise native
   heap analysis. Keep Swift/Objective-C graph coverage explicit.

The watcher must operate while the diagnostics panel is closed, in a scope owned
by the host. The memory reader still polls only while the overlay is present.
Full heap capture is an explicit diagnostic action with cancellation/status and
local storage; raw dumps may contain application data and are not sent anywhere
by opening a tool. Exported summaries can use safe metadata without raw values.

## Acceptance before implementation

Use injected clocks and weak handles for deterministic state-machine tests:
collected before deadline, retained at deadline, collected after reporting,
repeated labels with distinct identities, bounded eviction, stop/clear during
an in-flight check, unsupported source, concurrent registration and batched GC.
No real sleeps or exact real-GC timing in contract tests.

Add platform tests separately: released and deliberately rooted controls;
validate the chosen Shark version against an actual JVM/Android HPROF. The native
conversion experiment must compare reachability before and after releasing a
known root. Compilation alone does not validate a leak detector.

## Upstream reuse

Prefer dependency integration for Shark. When adapting LeakCanary source, record
the upstream commit and file, retain its copyright/license notices, mark changes
and update third-party attribution. Its repository uses Apache-2.0. Keep adapted
platform code outside commonMain and preserve the regression tests that motivated
upstream behavior.
[LeakCanary license](https://github.com/square/leakcanary/blob/main/LICENSE.txt).
