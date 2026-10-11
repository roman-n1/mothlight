# Stability boundaries

Mothlight runs inside its host process. Diagnostic observation must preserve the
host operation, and subscriptions must stop with their owner. This is enforced
with regression tests that inject failures through public APIs, including real
SQLite and HTTP adapter pipelines.

| Boundary | Behavior under failure |
| --- | --- |
| Memory overlay preferences | A failed read starts with the overlay off. A failed write keeps the switch usable in memory; later changes retry persistence. |
| Database observation | Subscription creation is deferred until collection, so both setup failures and query failures reach the same Flow error handler. A new collection retries setup. |
| SQLite tracing | A failed diagnostic gate, clock or recorder drops tracing for that observation. Queries, statement reuse and closing still execute. The delegate's original failures remain visible to its caller. |
| HTTP request capture | OkHttp never reads upload bodies. Ktor captures already materialized text only; custom byte-array and streaming content are left for the transport to read. |
| HTTP response capture | Capture failures omit diagnostic body data. Ktor reads at most `bodyLimit + 1` bytes from its observer copy and rejects oversized bodies. OkHttp uses a bounded peek. Original response content is not replaced. |
| JSON masking | Capture rejects nesting deeper than 64 containers before recursive parsing/masking. Rejected content is omitted in full, including any secrets. |
| Memory and retained-object observations | Existing recovery and virtual-time tests cover failed reads/checks, cancellation, pause, clear and owner shutdown. |

Fallback handling catches recoverable exceptions at diagnostic boundaries.
Coroutine cancellation still propagates. Fatal errors such as out-of-memory are
not converted to successful results. Invalid construction parameters and duplicate
tool IDs remain explicit programmer errors.

## Custom Compose tools

A custom tab runs in the host's Compose composition. Its own rendering, click
callbacks and coroutine effects remain application code. Mothlight cannot isolate
arbitrary exceptions in that code as if it ran in a separate process. Handle
expected IO failures inside the tool, expose an error state, and rethrow
`CancellationException`. Own long-lived state outside the tab and tie temporary
subscriptions to `LaunchedEffect` or `DisposableEffect`.

## Continuous checks

The required `jvm-android` job executes JVM regression tests and builds the Android
release sample with R8. The required `multiplatform` job executes core/SQLite/Ktor
contracts on the iOS simulator and core/Ktor contracts in Node for both JS and Wasm,
in addition to platform compilation. All jobs have explicit timeouts, so a stuck
check cannot report success. Source checks continue to ban nullable contracts and
reflection; resources and packaged consumer rules are validated separately.

These checks do not establish that all applications, devices or custom tools are
crash-free. Device UI smoke and representative host load tests remain necessary;
see [verification](verification.md) for tested platforms and remaining gaps.
Heap capture is an explicit, expensive action and should not run automatically in
an end-user session. Keep body capture disabled for production/streaming clients
unless the host explicitly needs and budgets for it.
