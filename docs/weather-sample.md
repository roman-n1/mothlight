# Weather integration sample

`sample-android` is a native Android Views app, with no Compose source or compiler
plugin. Its weather scenario is inspired by the adjacent Actron weather example;
it has no Actron dependency or state-management framework requirement.

## Run

With JDK 21 and Android SDK 37 configured:

```shell
./gradlew :sample-android:installDebug
adb shell am start -n io.github.mothlight.sample.android/.weather.WeatherActivity
```

Or run `sample-android` from Android Studio. The app needs Internet access, but no
account, location permission or API key. It offers fixed coordinates for
Novosibirsk, Lisbon and Tokyo. Controls follow English/Russian locale settings.

1. Select a city and press **Refresh**. The app calls the real
   [Open-Meteo forecast API](https://open-meteo.com/en/docs) through Retrofit/OkHttp.
2. Open **HTTP** in the embedded panel. Inspect the status and JSON response.
   Coordinates are query parameters; Mothlight removes the query string from its
   recorded URL. Requests have a 15-second timeout.
3. Open **Database**, then `weather_readings`. Each successful refresh inserts an
   observation. Keep the table open across another refresh to see invalidation.
4. Open **SQL** for the queries executed by the application's SQLite driver.
5. Open **Logs** or press **Write test log**. `Log.i` reports refresh/save/failure
   labels; the button writes a warning. Only this app's `MothlightWeather` tag is
   collected. The Logcat status is shown above the panel.
6. Close/reopen the app. Saved observations remain in `mothlight-weather.db`.
   Turn off connectivity and refresh: failure is shown without deleting history.

The screen shows the last three observations; the DAO emits the last 20 and keeps
at most 200 rows on disk. Timestamps are local forecast times reported by the API.
**More diagnostics** opens the existing synthetic HTTP/realtime/retention demo;
that screen remains independent and uses its original in-memory database.

Weather data is attributed in the app to [Open-Meteo](https://open-meteo.com/)
under CC BY 4.0. The hosted free API is for non-commercial use; check the provider's
[terms](https://open-meteo.com/en/terms) before adapting this example commercially.

## Where integration happens

| File | Responsibility |
| --- | --- |
| [WeatherActivity](../sample-android/src/main/kotlin/io/github/mothlight/sample/android/weather/WeatherActivity.kt) | Owns the recorder, client, Room database, panel and cancellable Logcat capture |
| [OpenMeteoRemote](../sample-android/src/main/kotlin/io/github/mothlight/sample/android/weather/OpenMeteoRemote.kt) | Retrofit endpoint and JSON validation; closes response bodies |
| [WeatherRepository](../sample-android/src/main/kotlin/io/github/mothlight/sample/android/weather/WeatherRepository.kt) | Fetch, save, failure outcomes and controlled log labels |
| [WeatherDatabase](../sample-android/src/main/kotlin/io/github/mothlight/sample/android/weather/WeatherDatabase.kt) | Disk schema, bounded history and Room Flow |

One `Mothlight` instance connects these independent integrations:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(MothlightInterceptor(mothlight.network))
    .build()

val database = Room.databaseBuilder(context, WeatherDatabase::class.java, "weather.db")
    .setDriver(TracingSQLiteDriver(BundledSQLiteDriver(), mothlight.sql, { mothlight.enabled.value }))
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()

val tools = mothlightTools(mothlight) {
    tool(databaseTool(RoomDatabaseSource(database)))
}
container.addView(createMothlightView(context, mothlight, tools))
```

Imports and complete ownership/cleanup are in the Activity. Database tables are
read-only in the panel. SQL capture omits bind values. The sample enables JSON
capture for public weather data; leave it disabled for sensitive/streaming traffic.
The Logcat sanitizer is an identity function only because the sample emits fixed,
controlled labels; real applications must supply their own sanitizer.

The Activity closes Logcat on stop and cancels network/observation work, closes Room
and releases its client on destruction. The panel and capture are included in both
sample variants to exercise R8. Real apps should use debug-only integration as shown
in the [Android guide](android.md).

## Automated checks

```shell
./gradlew :mothlight-android:testDebugUnitTest :sample-android:testDebugUnitTest
./gradlew :sample-android:assembleDebug :sample-android:assembleRelease
# Requires a connected Android device or emulator:
./gradlew :sample-android:connectedDebugAndroidTest
```

Unit tests use a local HTTP server, not the public weather service. They verify
Retrofit interception, readable response bodies, request parameters, HTTP/invalid
payload failures, cache preservation, cancellation and Logcat ownership. The
`android-database` CI job runs the real Room integration on an API 29 emulator:
file reopen, live inspector invalidation, SQL capture and history retention limits.
These checks do not assert public API availability or replace a manual visual smoke.
