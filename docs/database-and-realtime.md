# Database, SQL and realtime tools

## Room 2 and Room 3

Add `mothlight-room` for `androidx.room` 2.8.4, or `mothlight-room3` for
`androidx.room3` 3.0.0. Each module targets Android, JVM and iOS. Neither is
required for HTTP, logs, sockets or a custom database source. Room dependencies
remain outside `mothlight-core` and `mothlight-compose`.

Use a database built with `setDriver`, then attach that same app-owned instance:

```kotlin
import io.github.mothlight.room.RoomDatabaseSource // or .room3.RoomDatabaseSource
import io.github.mothlight.sqlite.TracingSQLiteDriver
import io.github.mothlight.compose.databaseTool

val driver = TracingSQLiteDriver(
    BundledSQLiteDriver(), mothlight.sql, { mothlight.enabled.value },
)
val database = /* your existing Room builder */
    .setDriver(driver)
    .build()
val source = RoomDatabaseSource(database)
val tools = MothlightTools(defaultMothlightTools(mothlight).entries + databaseTool(source))
```

Pass `tools` to `MothlightPanel` or Android's `createMothlightView`. A native
Android host does not need Compose source or the Compose compiler plugin.
Remember the tools in Compose. To attach multiple databases, give each tool a
unique ID, for example `databaseTool(cache, id = "cache-database")`.

The Room adapters use reader connections and InvalidationTracker. A DAO write
refreshes the currently visible page. No polling, global registry or retained
collector is created. Switching away or removing the panel cancels observation.
The host closes its database when appropriate; Mothlight never closes it.
Errors produce a localized unavailable state rather than a silent frozen view.
Legacy Room databases built with SupportSQLiteOpenHelper rather than setDriver
are not supported by these adapters.

The browser lists user tables and reads pages (50 rows by default, capped at
200). Identifiers must match discovered tables and are quoted. Paging orders by
all columns for stable ordering in an unchanged table; inserts/deletes can shift
offsets. Counts and rows are separate reads, not a transactional snapshot.
There is no arbitrary SQL execution or mutation API in the browser.

NULL is preserved; BLOB bytes are not retained. Cell text is capped and known
sensitive column names are masked through `Redactor`. Supply additional sensitive
keys for your schema. This is not general-purpose anonymization: non-sensitive
columns can still contain private application data.

## SQL trace

Wrap an AndroidX SQLiteDriver with `TracingSQLiteDriver` before giving it to
Room or another SQLite consumer. No reflection or host keep rules are needed by
the wrapper. Room's normal generated implementation rules still apply.

The trace reports each stepped statement execution at reset/close, including
reused prepared statements, errors, execution count and total/last duration.
Duration is statement lifetime from prepare/reset to reset/close, **not CPU time**.
A long-lived open statement has no completed event yet. Bind arguments are never
captured. SQL itself can contain literal values: use prepared parameters and
avoid sensitive literals when enabling diagnostics. Connection/prepare failures
before a statement exists are not recorded. Errors from step are rethrown.

The event feed and distinct-query statistics have independent bounds. Totals
survive event eviction until their query is evicted or history is cleared.
Mothlight's own reads carry a marker and are excluded. Repeated SQL is observable;
it is not proof of a Flow subscription. The original app's timing-based
subscription heuristic is deliberately not presented as a fact.

## Custom databases

Implement `DatabaseSource` with `tables`, bounded `page`, and cold `changes`.
`changes` must emit an initial signal after registering observation, followed by
invalidations. It must release listeners on cancellation. Return sanitized cells.
`DatabaseBrowser` refreshes on those signals without starting a background scope.

For an AndroidX SQLite connection, `SQLiteDatabaseSource` takes a `SqlReadExecutor`
and an invalidation-flow factory. The executor owns dispatching and connection
lifetime. Use `readResult` on the statement to cap results and omit blobs.

## Realtime

The default panel includes a channel-filtered frame feed and separate connection
history. Attach these passive hooks to a transport you already own:

```kotlin
mothlight.realtime.connection("chat", ConnectionState.CONNECTED, nowMillis)
mothlight.realtime.record("chat", FrameDirection.INCOMING, receivedText, nowMillis)
mothlight.realtime.record("chat", FrameDirection.OUTGOING, outgoingText, nowMillis)
```

Channel names are host-defined safe labels. There is no dependency on Centrifuge,
a messaging backend, a call stack, or a particular WebSocket implementation.
The hooks do not consume streams, reconnect sockets or create subscriptions.
They must be called by the host; automatic WebSocket transport adapters are not
included. Binary frames need an application-owned safe representation.

Payloads are omitted by default. With `Redactor(captureJsonBodies = true)`, JSON
is masked before entering the bounded feed. Oversized and non-JSON bodies are
omitted. Recording follows the same Mothlight gate as HTTP. Clearing removes both
frames and connection history without reusing IDs.

## Scope

The common contracts and UI also compile for JS/Wasm. Room and synchronous
SQLite adapters in this tranche target Android/JVM/iOS; web hosts can implement
DatabaseSource. Runtime browser/database and physical-device smoke remain separate
from compilation and host tests.
