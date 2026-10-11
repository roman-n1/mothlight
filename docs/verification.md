# Verification

The [Verify workflow](https://github.com/roman-n1/mothlight/actions/workflows/verify.yml)
checks the library from a clean checkout using public dependency repositories.
Its run history is the source of truth for the current commit's pass/fail status.

| Area | Automated check |
| --- | --- |
| Null contracts | Dedicated `null-contracts` job: tokenizer regression tests and all handwritten library production source sets, with no baseline or suppression |
| Core | JVM tests: gate, capacity, isolation, HTTP lifecycle, redaction, concurrency, cURL, live source cancellation, SQL statistics, realtime capture, typed diagnostics and concurrent writers |
| Compose | JVM tests: retention filtering and panel selection, mapper behavior, detail formatting, tool registration contracts, database paging, SQL/channel filters, startup waterfall, lifecycle balance, invariant priority |
| Retention | Common observation and analysis lifecycle tests; JVM concurrent registration/key retry; iOS real released-versus-rooted weak references |
| Shark | Real HotSpot dumps: marker IDs, rooted path, duplicate watches, independent sessions and released control; synthetic weak-only vs strong graph; corrupt/oversized/cancelled capture cleanup |
| Memory | Virtual-time start/stop/cancellation/recovery tests; live JVM heap, Linux resident readings and explicit Windows process unavailability; iOS physical footprint test |
| Ktor | MockEngine contracts on JVM, iOS, JS and Wasm: response preservation, masking, disabled capture, error transparency, one-shot upload and bounded streaming capture |
| OkHttp / Retrofit | Real OkHttp/Retrofit pipelines with a terminal mock transport, including failed capture metadata |
| SQLite | Real bundled SQLite tests on JVM and iOS simulator: bounded/masked pages, identifier rejection, tracing, statement reuse and errors |
| Room 2 / Room 3 | Real generated JVM databases: DAO writes invalidate and refresh the observed page |
| Desktop | Room 3 sample Kotlin/JVM compilation |
| Android | Native Views weather sample debug/release R8 builds; JVM repository, HTTP and Logcat lifecycle tests; API 29 emulator verifies persistent Room, invalidations, SQL capture and bounded history |
| iOS | Core, SQLite and Ktor tests in the arm64 simulator; Compose, Ktor and both Room adapters compiled for the simulator |
| Web | Core and Ktor runtime tests in Node on JS and Wasm, plus Compose and Ktor compilation |
| Shrinking | Reflection guard and consumer files packaged in all Android AARs; actual R8 renaming and preserved demo root checked from release mapping |
| Localization | Complete English/Russian keys and matching formatting arguments |

The Android sample has no Compose compiler plugin, verifying that the View adapter
can be consumed by a native Android project. The network module has no UI dependency.

## Test-first evidence

- Before the per-instance recording gate was implemented, its acceptance test
  failed on an unwanted retained event. It passes with the gate enabled.
- The first transport acceptance run reported four missing-record failures for
  the pass-through OkHttp stub; the disabled-capture scenario already passed.
  [Acceptance run](https://github.com/roman-n1/mothlight/actions/runs/38045308484).
- A malformed-body test caught a JSON-parser edge case: an unquoted scalar could
  be retained. Capture now accepts only JSON objects and arrays.
- Ktor error transparency compares type and message with an uninstrumented client.
  Reference identity is not part of the contract because coroutine stack recovery
  may copy the exception.

- The first live-tool tests failed on a missing database subscription and empty SQL/realtime feeds.
  [Live contract RED run](https://github.com/roman-n1/mothlight/actions/runs/38047718942).
- Real SQLite tests (JVM and iOS) and both real Room JVM tests then failed on empty
  table results and missing execution durations before the reader/wrapper implementation.
  [Database adapter RED run](https://github.com/roman-n1/mothlight/actions/runs/38047942604).

- Startup/lifecycle/navigation/invariant acceptance and UI mapper tests first failed
  on empty histories, missing bounds and empty presentation rows. A four-writer
  JVM test also failed on the missing timeline before atomic snapshot updates.
  [Diagnostics RED run](https://github.com/roman-n1/mothlight/actions/runs/38051064039).

- Memory settings, polling lifecycle, platform readings and presentation first
  failed against inert implementations (9 JVM failures).
  [Memory RED run](https://github.com/roman-n1/mothlight/actions/runs/38052246436).
- The first memory implementation exposed a cancellation-boundary error: cancellation
  of the inner sampling flow did not cancel the collector. The same acceptance
  test failed on JVM and iOS; observation now propagates source cancellation
  directly from the collecting coroutine without weakening that assertion.

- Retention observation, native weak reachability and presentation first failed
  against inert implementations on JVM, Windows and the iOS simulator.
  [Retention RED run](https://github.com/roman-n1/mothlight/actions/runs/38053741244).

- Reflection-free diagnostics first failed on the old exception class names in
  Ktor, OkHttp and SQLite (JVM and iOS); Windows failed on the removed JNA metric.
  [No-reflection RED run](https://github.com/roman-n1/mothlight/actions/runs/38054898119).
- The local shrinker guard first failed on missing consumer wiring, then identified
  JNA and the three runtime class-name lookups before their removal.

- Heap-analysis identity first failed on a real JVM dump: the watched reference
  had no stable identifier for the analyzer.
  [Heap identity RED run](https://github.com/roman-n1/mothlight/actions/runs/38056908180).

- Desktop capture failed with the same real-heap test when only the cache path
  was changed to contain spaces. The adapter now quotes the JVM diagnostic-command
  argument separately from process arguments and tests both quote styles.
  [Desktop path RED run](https://github.com/roman-n1/mothlight/actions/runs/38057780495).

- The null-contract gate rejects the pre-migration main with 115 violations across
  29 files, including nullable declarations, assertions and an unbounded generic.
  The SQLite malformed-count test first failed with `NullPointerException` where
  an explicit executor-contract failure was expected. Both are covered by the
  null-free migration's checks and tests.

- Weather sample and Logcat acceptance tests first failed against inert implementations:
  5 Logcat failures and 3 repository failures (the offline-preservation case already
  passed). The implemented path passes without changing those assertions.

## Failure-injection regression tests

[Stability boundaries](stability.md) documents fallback and cancellation contracts.
The suite injects failed preference storage, failed database subscription setup,
SQLite gate/clock/recorder failures, broken response metadata, one-shot Ktor uploads,
and a JSON stream held open after exceeding its capture limit. JSON nesting limits
are tested at the accepted/rejected boundary and with escaped quotes in string data.
These tests run inside required CI jobs; job timeouts bound stuck runs.

## Limits

No Android device/emulator UI smoke, iOS panel rendering, browser runtime smoke,
or Desktop visual smoke has been performed. Compilation does not establish UI or
network behavior on those devices. iOS device arm64 is declared but only the simulator
variant is compiled in the current CI.

The originating application has not yet switched to Mothlight. No Maven Central
artifacts or stable release have been published; consume this alpha from source.
