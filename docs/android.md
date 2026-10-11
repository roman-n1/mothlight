# Native Android and Retrofit

You can use Mothlight from an Android-only project. Retrofit requires no special
Mothlight converter or call adapter: attach the interceptor to its OkHttp client.
The interceptor module has no Compose dependency.

## Network capture

During the alpha, add `includeBuild("../mothlight")` to settings. For debug-only
integration, put Mothlight-specific wiring in `src/debug` and expose an app-owned
interface with a no-op implementation in `src/release`.

```kotlin
// app/build.gradle.kts
// The library is not yet on Maven Central; these coordinates use composite substitution.
debugImplementation("io.github.roman-n1.mothlight:mothlight-okhttp:0.1.0-SNAPSHOT")
debugImplementation("io.github.roman-n1.mothlight:mothlight-android:0.1.0-SNAPSHOT")
```

```kotlin
import io.github.mothlight.Mothlight
import io.github.mothlight.okhttp.MothlightInterceptor

val mothlight = Mothlight().apply { setEnabled(true) }
val client = OkHttpClient.Builder()
    .addInterceptor(MothlightInterceptor(mothlight.network))
    .build()
val retrofit = Retrofit.Builder()
    .baseUrl("https://your-api.example/")
    .client(client)
    .build()
```

Use an **application interceptor** (`addInterceptor`). It records the logical call;
individual redirects/retries are not presented as separate network attempts.
Existing Retrofit converters and call adapters remain unchanged. The adapter is
built against OkHttp 4.12; the sample and integration test use Retrofit 3.0.

The caller receives its original response body. Request bodies are deliberately
not read or serialized again, including one-shot and duplex uploads. Optional
response capture uses a bounded `peekBody`. Transport errors propagate unchanged;
only the stable `TransportError` category enters the panel.

## Activity or Fragment using Views

```kotlin
import io.github.mothlight.android.createMothlightView

// A ComponentActivity/AppCompatActivity or Fragment view tree owns the lifecycle.
container.addView(createMothlightView(requireContext(), mothlight))
```

The adapter returns a `ComposeView` and disposes its composition with the view-tree
lifecycle. The consuming app does not need composable functions or the Compose
compiler plugin. It does include the Compose runtime transitively.

Java callers can use `MothlightViews.createMothlightView(context, mothlight)`.
Compose apps can use `MothlightPanel` directly and supply their own `MaterialTheme`.

## Locale

The panel follows the application's Compose resource environment. English is the
fallback and Russian has a complete resource set. Native sample controls use Android
resources. Configure app languages through the host's locale mechanism; the library
does not change the global application locale.

## Build and shrinking

```shell
./gradlew :sample-android:assembleDebug :sample-android:assembleRelease
```

The native sample has no Compose compiler plugin. Its release build enables R8.
Mothlight uses typed calls and declares no broad keep rules; Retrofit/OkHttp
supply their own consumer rules. The sample is for development and intentionally
includes the panel in both variants to exercise shrinking. Production apps should
keep debug wiring out of release variants.

See [verification](verification.md) for build/device status.

See [minification](minification.md) for the shared consumer rules and automated AAR/R8 verification.

## Android Logcat

`mothlight-android` can read the current process's main Logcat buffer. It does not
request `READ_LOGS`, root access or logs from other applications. Android/device
restrictions can still make the reader unavailable.

```kotlin
import io.github.mothlight.android.captureLogcat

val capture = captureLogcat(
    scope = screenScope,
    mothlight = mothlight,
    tags = setOf("MyApp"),
    sanitize = { message -> scrubApplicationSecrets(message) },
)
// Observe capture.status: STOPPED, READING or UNAVAILABLE.
// When this owner stops:
capture.close()
```

The sanitizer is required; HTTP redaction does not sanitize free-text logs.
An empty tag set accepts all tags from this process. Entries have unique IDs and
use the existing bounded Logs feed; messages are capped at 4,000 characters.
Verbose maps to DEBUG, Fatal to ERROR. Non-record lines are ignored; multiline
messages appear as separate Logcat records. Capture starts from the last buffered
line, so this is a live view, not a complete historical export.

Disabling Mothlight terminates the reader; re-enabling starts a new one. Cancelling
the owner scope or calling `close()` terminates capture permanently. An unavailable
reader does not retry in a loop: toggle recording to retry. `READING` means the
reader is active, not that every device log is available. No logging framework is
required; `android.util.Log` works. For KMP or guaranteed delivery, use the direct
`mothlight.logs.record(...)` API instead of relying on platform Logcat access.

[Weather sample](weather-sample.md) connects all four tools in one native Activity.
