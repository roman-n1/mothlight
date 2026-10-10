# Verification

The [Verify workflow](https://github.com/roman-n1/mothlight/actions/workflows/verify.yml)
checks the library from a clean checkout using public dependency repositories.
Its run history is the source of truth for the current commit's pass/fail status.

| Area | Automated check |
| --- | --- |
| Core | 23 JVM tests: gate, capacity, isolation, HTTP lifecycle, redaction, concurrency, cURL, live source cancellation, SQL statistics, realtime capture, typed diagnostics and concurrent writers |
| Compose | 8 JVM tests: mapper behavior, detail formatting, tool registration contracts, database paging, SQL/channel filters, startup waterfall, lifecycle balance, invariant priority |
| Ktor | 4 JVM tests through MockEngine: response preservation, masking, disabled capture, error transparency |
| OkHttp / Retrofit | 5 JVM tests through real OkHttp/Retrofit pipelines with a terminal mock transport |
| SQLite | Real bundled SQLite tests on JVM and iOS simulator: bounded/masked pages, identifier rejection, tracing, statement reuse and errors |
| Room 2 / Room 3 | Real generated JVM databases: DAO writes invalidate and refresh the observed page |
| Desktop | Room 3 sample Kotlin/JVM compilation |
| Android | Native Views + Retrofit + Room 2 sample debug APK and release APK with R8 enabled |
| iOS | Core and SQLite tests in the arm64 simulator; Compose, Ktor and both Room adapters compiled for the simulator |
| Web | Compose and Ktor compilation for JS and Wasm, including core dependencies |
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

## Limits

No Android device/emulator UI smoke, iOS panel rendering, browser runtime smoke,
or Desktop visual smoke has been performed. Compilation does not establish UI or
network behavior on those devices. iOS device arm64 is declared but only the simulator
variant is compiled in the current CI.

The originating application has not yet switched to Mothlight. No Maven Central
artifacts or stable release have been published; consume this alpha from source.
