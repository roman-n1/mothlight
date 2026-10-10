# Mothlight

[![Verify](https://github.com/roman-n1/mothlight/actions/workflows/verify.yml/badge.svg)](https://github.com/roman-n1/mothlight/actions/workflows/verify.yml)

**See what your app is doing.**

An extensible, in-app developer panel for Kotlin Multiplatform and native Android.
Inspect HTTP traffic, live database rows, SQL execution and realtime frames.
Inspect startup milestones, lifecycle observations, navigation breadcrumbs and
invariant counters. Collect logs and add tools owned by your app.

> Early alpha. This repository contains the reusable foundation extracted from an
> existing application. It does not include that application's authentication,
> messaging, call stack, or product-specific tools. No Maven Central release yet.

## What's inside

| Module | Purpose | Needs Compose in your app? |
| --- | --- | --- |
| `mothlight-core` | Instance-owned recording, bounded feeds, HTTP model and redaction | No |
| `mothlight-compose` | Material 3 panel and custom tool API | Yes |
| `mothlight-ktor` | Passive Ktor client integration | No |
| `mothlight-okhttp` | OkHttp interceptor, also works with Retrofit | No |
| `mothlight-sqlite` | SQLiteDriver tracing and read-only table source | No |
| `mothlight-room` | Room 2 live database adapter (Android/JVM/iOS) | No |
| `mothlight-room3` | Room 3 live database adapter (Android/JVM/iOS) | No |
| `mothlight-android` | View factory for Activities and Fragments | No Compose source/compiler required |
| `sample-desktop` | Interactive demo with synthetic Ktor responses and a custom tab | Demo |
| `sample-android` | Native Android Views + Retrofit demo, synthetic responses | No |

English is the fallback language. Russian is selected automatically for a Russian
application locale. Panel strings, hints and sample controls are localized.
Application-supplied log messages, exception type names and HTTP protocol values
are data and are not translated.

## Try it

Requires JDK 21 for the build and Android SDK 37. JVM artifacts target Java 17.
The source build uses Kotlin 2.4.10, Compose Multiplatform 1.11.1 and AGP 9.3.0.
Android minimum SDK: 23.

```shell
./gradlew :sample-desktop:run
./gradlew :sample-android:assembleDebug
```

The samples use mock transports. They do not contact a backend or require credentials.

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

Imports: `io.github.mothlight.Mothlight`,
`io.github.mothlight.ktor.installMothlight`, and
`io.github.mothlight.compose.MothlightPanel`.

**Native Android + Retrofit:** see [the Android guide](docs/android.md). Neither
KMP nor Compose source code is required in a consuming Android app.

## Database and realtime

The default panel includes SQL and realtime tools. Attach a Room database with
`databaseTool(RoomDatabaseSource(database))` to browse live table pages. See the
[database and realtime guide](docs/database-and-realtime.md) for Room 2/3, SQLite
tracing, native Android and transport hooks.

The [extraction inventory](docs/extraction-roadmap.md) tracks other tools from the
original panel and distinguishes shipped capabilities from remaining work.

## Typed diagnostics

Startup, lifecycle, navigation and invariant tools are included in the default
panel. Report milestones/transitions through `mothlight.startup`, `.lifecycle`,
`.navigation` and `.invariants`. All are instance-owned and follow the recording
gate. See the [diagnostic integration guide](docs/diagnostics.md) for host hooks,
clock semantics, bounded retention, sample redaction and interpretation limits.

## Extend it

Own your data and actions in your app. Only the selected tool is composed; tools
can collect their own flows and use their own localized resources.

```kotlin
val tools = MothlightTools(
    defaultMothlightTools(mothlight).entries + MothlightTool(
        id = "my-database",
        title = { stringResource(Res.string.database) },
        content = { modifier -> MyDatabaseInspector(modifier) },
    )
)
MothlightPanel(mothlight, tools = tools)
```

Tool IDs must be nonblank and unique. Remember the `MothlightTools` instance in
Compose to retain selection across recompositions. Do not launch work from a tool
constructor; tie it to the tool's composition or your explicitly owned lifecycle.

Record logs or diagnostic events through the core API:

```kotlin
mothlight.logs.record(LogEntry("log-1", nowMillis, LogLevel.INFO, "Cache", "Ready"))
mothlight.events.record(DiagnosticEvent("event-1", nowMillis, "startup", "Database ready"))
```

Use unique IDs per feed and scrub application-supplied messages before recording.

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
may buffer a response before the stored body limit is applied. The OkHttp adapter
uses a bounded peek and does not replay request bodies. A cURL export uses recorded,
redacted data; it is not an authenticated replay facility.

## Development

```shell
python3 scripts/check_resources.py
./gradlew :mothlight-core:jvmTest :mothlight-compose:jvmTest :mothlight-ktor:jvmTest :mothlight-okhttp:jvmTest
```

See [architecture and extraction](docs/architecture.md), [Android](docs/android.md)
and [verification](docs/verification.md).

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
