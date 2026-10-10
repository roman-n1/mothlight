# Verification

The [Verify workflow](https://github.com/roman-n1/mothlight/actions/workflows/verify.yml)
checks the library from a clean checkout using public dependency repositories.
Its run history is the source of truth for the current commit's pass/fail status.

| Area | Automated check |
| --- | --- |
| Core | 13 JVM tests: gate, capacity, isolation, HTTP lifecycle, redaction, concurrency, cURL |
| Compose | 3 JVM tests: mapper behavior, detail formatting, tool registration contracts |
| Ktor | 4 JVM tests through MockEngine: response preservation, masking, disabled capture, error transparency |
| OkHttp / Retrofit | 5 JVM tests through real OkHttp/Retrofit pipelines with a terminal mock transport |
| Desktop | Sample Kotlin/JVM compilation |
| Android | Native Views + Retrofit sample debug APK and release APK with R8 enabled |
| iOS | Core tests in the arm64 simulator; Compose and Ktor arm64 simulator compilation |
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

## Limits

No Android device/emulator UI smoke, iOS panel rendering, browser runtime smoke,
or Desktop visual smoke has been performed. Compilation does not establish UI or
network behavior on those devices. iOS device arm64 is declared but only the simulator
variant is compiled in the current CI.

The originating application has not yet switched to Mothlight. No Maven Central
artifacts or stable release have been published; consume this alpha from source.
