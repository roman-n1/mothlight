# Verification

The [Verify workflow](https://github.com/roman-n1/mothlight/actions/workflows/verify.yml)
defines automated checks. Its run history establishes CI status for a commit;
local results are reported separately.

| Area | Automated check |
| --- | --- |
| Null contracts | Tokenizer regression tests and all handwritten library production source sets; no baseline or suppression |
| Core | JVM tests for gates, bounded logs, HTTP lifecycle, redaction, concurrency, cURL, database observation, SQL statistics and startup milestones |
| Compose | JVM tests for HTTP filters, JSON node traversal/limits, log filtering/export/relative clocks, details/search, tool registration, grouped tools, database paging, SQL statistics, startup waterfall and memory presentation |
| Memory | Virtual-time polling/recovery/cancellation tests and platform readings |
| Ktor | MockEngine response preservation, masking, disabled capture, errors, one-shot upload and bounded streaming capture |
| OkHttp / Retrofit | Real client pipelines with a terminal mock transport |
| SQLite | Bundled SQLite tests for bounded/masked pages, column metadata, generated columns, identifiers, tracing, statement reuse and errors |
| Room 2 / Room 3 | Generated JVM databases; schema metadata matches Room columns; DAO writes invalidate the observed page |
| Desktop | Room 3 sample tests for persistence, migrations, reader behavior, dock interactions, HTTP navigation/filter/state/clipboard/JSON folding, log filtering/copy/follow/reading anchors/eviction, grouped-tool disposal/selection, data/schema switching and live host-theme changes |
| Android | Native Views + Retrofit + Room 2 sample debug and minified release APKs |
| iOS | Core, memory, SQLite and Ktor simulator tests; Compose and database adapter compilation |
| Web | Core/Ktor runtime tests in Node on JS/Wasm; Compose and Ktor compilation |
| Shrinking | Consumer-rule packaging, reflection guard and actual R8 class renaming |
| Localization | Matching English/Russian keys and formatting arguments |

The desktop sample normally uses injected MockEngine fixtures in tests. Set
`MOTHLIGHT_LIVE_HTTP=1` to opt into live HTTPS against the public DEV API, with
articles stored in Room and requests visible through Mothlight. The Android
sample uses a mock transport and requires no Compose compiler plugin in the host.

## Local desktop and web builds

Run `./gradlew :sample-desktop:run` for the desktop example, or
`./gradlew :sample-desktop:createDistributable` to build an application image.
`./gradlew :sample-desktop:runDistributable` launches that image.
The sample uses a full JDK 21 toolchain for compilation and packaging; Android
Studio's bundled runtime may lack `jpackage`. Native images include the extra
JDK modules reported by `suggestModules`. macOS packaging uses version `1.0.0`
because `jpackage` rejects a zero major version and `-SNAPSHOT` suffixes.

Web builds use Yarn and the checked-in `kotlin-js-store` lockfiles. If dependencies
change, use `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`.
`kotlinWasmUpgradePackageLock` belongs to the npm configuration and expects a
`build/wasm/package-lock.json`; it is not the lock update task for this project.

## Limits

Compilation does not establish device UI behavior. Android device UI smoke, iOS
panel rendering and browser runtime smoke remain separate checks. iOS device arm64
is declared; CI compiles the simulator variant. The originating application has
not yet switched to Mothlight. No Maven Central release is published.
