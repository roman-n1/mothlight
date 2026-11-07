# Mothlight

<p align="center">
  <img src="assets/brand/mothlight.png" width="400" alt="Mothlight — Bring bugs to light. Five moths gathering around a glowing lantern.">
</p>

[![Verify](https://github.com/roman-n1/mothlight/actions/workflows/verify.yml/badge.svg)](https://github.com/roman-n1/mothlight/actions/workflows/verify.yml)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

**Bring bugs to light.**

An extensible, in-app developer panel for **Kotlin Multiplatform and native Android**.
Inspect network traffic, browse live database rows, read logs and
watch memory — inside the app you're debugging.

A request failed. A row changed, but the screen didn't. Startup took longer than expected. Mothlight brings the evidence into view so you can follow what happened.
The lantern is your debug panel; the moths are the bugs coming into the light.

[Try the demo](#try-it) · [Add to your app](#use-from-source) ·
[Android + Retrofit](docs/android.md) · [Build a tool](#extend-it)

> **Early alpha · available from source.** No Maven Central release yet.
> Mothlight extracts reusable debugging tools from a working application;
> authentication, messaging, calls and product-specific tools stay with the host.

## Null-free by design

**Totally null-free contracts. Enforced in CI.**

Mothlight's handwritten library code declares no nullable types and uses no `null`
literals, `!!` assertions or `Optional` / `Option` / `Maybe` containers. Missing data
has a meaningful domain state: a request is `InProgress`, a body is `NotCaptured`,
a memory reading is `Unavailable`. Callers work with those states explicitly.

This is a checked rule for future changes, too. The required
[`null-contracts` CI check](.github/workflows/verify.yml) scans every library
production source set, including newly added modules. New violations block merging
into [protected `main`](https://github.com/roman-n1/mothlight/rules/24845776).
There is no baseline, suppression or file allow-list.

The guarantee covers Mothlight's own production contracts. Platform and third-party
APIs may still return missing values; adapters handle those at the boundary. Tests,
samples and generated code are outside the rule. See the
[null-free policy and migration guide](docs/null-contracts.md) for the exact scope.

## What will you bring to light?

| Follow the question | Find the evidence |
| --- | --- |
| **What went over the wire?** | Inspect HTTP traffic through Ktor or OkHttp/Retrofit, with redacted headers and optional JSON capture. |
| **What's really in the database?** | Browse live Room 2/3 table pages and trace SQLite execution. |
| **How did the app get here?** | Record startup milestones through explicit host hooks. |
| **Where is memory going?** | Keep platform memory readings visible over the app and inspect objects that outlive their expected lifetime. |
| **What does my app need to explain?** | Add your own tools and localized tabs through the panel's plugin API. |

### Your app, your light switch

- **Start with the tools you need.** Recording and adapters are separate modules;
  the core has no Compose dependency. No Decompose or DI framework is required.
- **Use it in native Android, too.** View factories fit Activities and Fragments;
  the OkHttp adapter works with Retrofit. Your app needs no Compose source or compiler plugin.
- **Keep control of capture.** Recording starts disabled, buffers are bounded,
  and each instance belongs to your app. No upload endpoint or telemetry service is included.
- **Extend it with your own knowledge.** Add host-owned screens for behavior only your app understands.
- **Read it in English or Russian.** Library controls follow the app locale,
  with English as the fallback.

## Try it

Requires JDK 21 for the build and Android SDK 37. JVM artifacts target Java 17.
The source build uses Kotlin 2.4.10, Compose Multiplatform 1.11.1 and AGP 9.3.0.
Android minimum SDK: 23.

```shell
./gradlew :sample-desktop:run
./gradlew :sample-android:assembleDebug
```

The desktop sample is **Leaf (Лист)**, a small reading app. Refresh the feed,
follow publications and save articles. Articles, subscriptions and bookmarks
live in Room at `~/.mothlight-demo/reader.db` and survive restarts. The UI observes
Room flows; refreshing the catalog preserves subscriptions and bookmarks.

Open **Debug panel** to inspect the HTTP requests, SQL and logs
created by those actions. Drag the separator to resize the panel, or choose
**Dock right / Dock bottom**. Each placement retains its size while the app runs.
The host remains interactive alongside the panel.

HTTP uses the Ktor CIO engine against the public [DEV API](https://developers.forem.com/api).
The feed loads up to 10 articles each for design, Kotlin and web development,
including author and description. **Read** opens the complete article text inside
the app and requests `GET /api/articles/{id}` through the same instrumented Ktor
client. The debug dock stays accessible beside the reader. Full text is cached in
a separate Room table, survives feed refreshes and restarts, and remains visible
if an update fails. The reader displays inert, selectable text with paragraph,
list and code line breaks; embedded media is represented by available alt text.
No API key is required. Requests have timeouts; failed refreshes leave the cached feed intact.
JSON capture is capped at 128 KiB per response in this sample. Query parameters
are redacted by Mothlight as usual.

The Android sample still uses a mock transport. Desktop automated tests inject
MockEngine for deterministic offline checks. To also run the live HTTPS smoke test:

```shell
MOTHLIGHT_LIVE_HTTP=1 ./gradlew :sample-desktop:test --rerun-tasks
```

Database migrations add author/source URL fields (version 2) and a separate full-text
cache (version 3) without clearing saved articles
or subscription choices. Unsaved synthetic articles are removed after the first
successful live refresh; saved synthetic articles remain in Saved.

Platform targets: Android, JVM, iOS arm64/simulator arm64, JS and Wasm. See
[verification](docs/verification.md) for what was actually tested; declaring a target
is not a claim of device validation.

## Use from source

Until the first Maven Central release, use a Gradle composite build. In your app's
`settings.gradle.kts`:

```kotlin
includeBuild("../mothlight")
```

Then add only the modules you need:

```kotlin
implementation("io.github.roman-n1.mothlight:mothlight-compose:0.1.0-SNAPSHOT")
implementation("io.github.roman-n1.mothlight:mothlight-ktor:0.1.0-SNAPSHOT")
```

Create and own one instance in your application graph. Recording starts disabled:

```kotlin
val mothlight = Mothlight()
mothlight.setEnabled(true) // development configuration

val client = HttpClient {
    installMothlight(mothlight.network)
}

@Composable
fun DebugScreen() {
    MaterialTheme { MothlightPanel(mothlight) }
}
```

The panel and memory overlay inherit the surrounding Material 3 theme: colors,
typography and shapes. Place them inside your app’s `MaterialTheme` to match the
host UI. The desktop sample shares one theme between the host and the panel;
use its light/dark toggle to switch both together.

For a resizable embedded panel, keep the dock state in the host:

```kotlin
val dockState = remember { MothlightDockState() }
MothlightDock(
    visible = debugVisible,
    modifier = Modifier.fillMaxSize(),
    placement = MothlightDockPlacement.End, // Bottom for a horizontal split
    state = dockState,
    panel = { MothlightPanel(mothlight) },
    content = { AppContent() },
)
```

Import these types from `io.github.mothlight.compose`. `End` is the right side
in left-to-right layouts and the left side in right-to-left layouts. The divider
resizes the panel between 25% and 75% of the available space and exposes an
accessibility progress action. Closing the dock does not disable recording.

Imports: `io.github.mothlight.Mothlight`,
`io.github.mothlight.ktor.installMothlight`, and
`io.github.mothlight.compose.MothlightPanel`.

**Native Android + Retrofit:** see [the Android guide](docs/android.md). Neither
KMP nor Compose source code is required in a consuming Android app.
Consumer rules are packaged automatically; see [minification](docs/minification.md)
for Android R8 and Windows/JVM considerations.

## HTTP inspector

For HTTP navigation, filtering, JSON node folding and copy actions, see the
[HTTP inspector guide](docs/http-inspector.md).

## Database and SQL

The default panel includes the SQL tool. Attach a Room database with
`databaseTool(RoomDatabaseSource(database))` to browse live table pages. See the
[database and SQL guide](docs/database.md) for Room 2/3, SQLite
tracing and native Android.

The [extraction inventory](docs/extraction-roadmap.md) tracks other tools from the
original panel and distinguishes shipped capabilities from remaining work.

## Startup milestones

The default panel includes a startup waterfall. Report milestones through
`mothlight.startup.mark("database_ready", nowMillis)`. See the
[startup guide](docs/diagnostics.md) for clock semantics and bounded history.

## Extend it

Add a tab for any of your own `@Composable` debug screens. The built-in tabs stay
available; register ready-made tools with `tool(...)` in the same block:

```kotlin
val tools = remember(mothlight, cache, databaseSource) {
    mothlightTools(mothlight) {
        tab(id = "my-cache", title = { stringResource(AppRes.string.debug_cache) }) { modifier ->
            CacheDebugScreen(cache, modifier)
        }
        tool(databaseTool(databaseSource))
    }
}
MothlightPanel(mothlight, tools = tools)
```

Import `io.github.mothlight.compose.mothlightTools`. `CacheDebugScreen`, `cache`,
`databaseSource` and `AppRes` belong to your app. Include captured dependencies in
the `remember` keys. No plugin interface, reflection or global registration is needed.

Only the selected tab's content is composed. Tool IDs must be nonblank and unique,
including against built-in tabs. Keep the tool set with `remember` and scope data
collection to the tab's composition. See [custom Compose tabs](docs/custom-tools.md)
for existing functions without a `Modifier`, localization, state and native Android.

Use `group("actron", title = { localizedTitle() }) { tab(...); tool(...) }` for
a tab with its own subtools. Child IDs are scoped to the group; the selected child
is remembered while the tool set lives. Existing Actron history/inspector screens
can be supplied by the host without coupling the two libraries. See
[grouped tools and Actron integration](docs/custom-tools.md#tabs-with-subtools).

Record logs through the core API:

```kotlin
mothlight.logs.record(LogEntry("log-1", nowMillis, LogLevel.INFO, "Cache", "Ready"))
```

Use unique log IDs and scrub application-supplied messages before recording.
The [log inspector](docs/log-inspector.md) adds search, severity/tag filters,
expandable messages, pause/follow controls and copy actions for recorded logs.

## Data capture

- All recording is opt-in; each instance owns its gate and buffers.
- HTTP authorization/cookie/API-key headers are masked before publication.
- URL credentials, query strings and fragments are removed. Paths remain visible.
- Request and response bodies are omitted by default.
- Opt-in JSON capture recursively masks known sensitive keys; custom sensitive keys
  can be supplied through `Redactor`. Non-JSON/oversized bodies are omitted.
- Redaction is not general-purpose anonymization. Review endpoint paths, custom
  headers, free text and custom tools for your application.
- Closing/removing the panel stops its UI collectors, but does not stop recording.
  Call `setEnabled(false)` to stop recording and `clear()` to remove retained data.
- No upload endpoint, telemetry service or analytics is included.

```kotlin
val mothlight = Mothlight(redactor = Redactor(
    captureJsonBodies = true,
    additionalSensitiveKeys = setOf("email", "customer_id"),
))
```

For streaming endpoints leave body capture disabled. Ktor's optional JSON observer
reads a bounded prefix; its underlying transport/observer may still buffer data.
The OkHttp adapter uses a bounded peek. Neither adapter replays custom upload bodies. A cURL export uses recorded,
redacted data; it is not an authenticated replay facility.

## What's inside

| Module | Purpose | Needs Compose in your app? |
| --- | --- | --- |
| `mothlight-core` | Instance-owned recording, bounded logs, startup milestones, SQL/HTTP models and redaction | No |
| `mothlight-memory` | Platform memory readings and cancellable observation | No |
| `mothlight-compose` | Material 3 panel and custom tool API | Yes |
| `mothlight-ktor` | Passive Ktor client integration | No |
| `mothlight-okhttp` | OkHttp interceptor, also works with Retrofit | No |
| `mothlight-sqlite` | SQLiteDriver tracing and read-only table source | No |
| `mothlight-room` | Room 2 live database adapter (Android/JVM/iOS) | No |
| `mothlight-room3` | Room 3 live database adapter (Android/JVM/iOS) | No |
| `mothlight-android` | View factory for Activities and Fragments | No Compose source/compiler required |
| `sample-desktop` | Reading app with live DEV HTTPS, persistent Room subscriptions/bookmarks and a resizable debug dock | Demo |
| `sample-android` | Native Android Views + Retrofit demo, synthetic responses | No |

English is the fallback language. Russian is selected automatically for a Russian
application locale. Panel strings, hints and sample controls are localized.
Application-supplied log messages, error categories and HTTP protocol values
are data and are not translated.

The optional [memory overlay](docs/memory.md) has a panel switch, per-instance
settings and Compose/native Android View integration. It reports memory usage.

## Development

Library contracts use explicit domain states instead of nullable values or
`Optional` containers. See the [null-free contracts and migration guide](docs/null-contracts.md).

```shell
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_null_contracts.py
python3 scripts/check_resources.py
./gradlew :mothlight-core:jvmTest :mothlight-compose:jvmTest :mothlight-ktor:jvmTest :mothlight-okhttp:jvmTest
```

See [architecture and extraction](docs/architecture.md), [Android](docs/android.md),
[stability boundaries](docs/stability.md) and [verification](docs/verification.md).

## Help bring the next bug to light

Try Mothlight in a development build. If an integration is missing, a tool is hard
to read or a capture behaves unexpectedly, [open an issue](https://github.com/roman-n1/mothlight/issues).
Include the platform, module and a small reproducible example with synthetic data.
Ideas for new adapters and app-owned tools are welcome, too.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
