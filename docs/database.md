# Database and SQL tools

## Room 2 and Room 3

Add `mothlight-room` for `androidx.room` 2.8.5, or `mothlight-room3` for
`androidx.room3` 3.0.3. Each module targets Android, JVM and iOS. Neither is
required for HTTP, logs or a custom database source. Room dependencies
remain outside `mothlight-core` and `mothlight-compose`.

The Beta 1 Maven Central release includes the Room 2 adapter.
Use the `mothlight-room3` project from source for Room 3.

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

The browser has a searchable table picker and a grid with a fixed header.
Select a row to read and copy its full captured cells, including columns past
the first 32 shown in the grid. The grid reads pages
(50 rows by default, capped at 200). Identifiers must match discovered tables and are quoted. Paging orders by
all columns for stable ordering in an unchanged table; inserts/deletes can shift
offsets. Counts and rows are separate reads, not a transactional snapshot.
There is no arbitrary SQL execution or mutation API in the browser.

NULL is preserved; BLOB bytes are not retained. Cell text is capped and known
sensitive column names are masked through `Redactor`. Supply additional sensitive
keys for your schema. This is not general-purpose anonymization: non-sensitive
columns can still contain private application data.

## Live row changes

On **Data**, the first read establishes a baseline for the current table and
page. On subsequent invalidations, the inspector compares captured cells by
primary key, including composite keys. Changed cells are highlighted in the
grid. **Changes** shows the latest non-empty batch with old and new values,
as well as rows that appeared or left the page. Other invalidations with no
visible differences preserve that batch.

The comparison covers the current page and resets when you change table or
page, open Schema or leave the inspector. Room may combine writes into one
invalidation. Rows can cross page boundaries, so appearing and leaving describe
page membership. Unavailable schema or ambiguous keys fall back to live page
refresh. Masked values, omitted BLOB bytes and text beyond the capture limit
cannot be compared.

## Column schema

Select a table, then **Schema** to inspect column names, declared types, the
declared NOT NULL flag, primary-key positions and hidden/generated column kinds.
Room 2, Room 3 and SQLite sources provide this metadata without extra setup.
The view follows the source invalidations while visible and stops collecting
when switching back to Data or leaving the tool.

`DatabaseSource.schema(table)` is an optional capability with a default
`DatabaseSchema.Unsupported` result, so existing custom sources need no changes.
Implement it to return `DatabaseSchema.Available` for custom backends;
`DatabaseBrowser.schema(table)` observes it using the same cold changes flow.

SQLite uses a generated read of `pragma_table_xinfo`, validates the table against
discovered names and escapes its argument. It returns at most 512 columns and
explicitly marks truncation. Defaults and generation expressions are never
selected; schema reads carry the panel marker to exclude them from SQL traces.
Types are SQLite declarations, not inferred types of stored values. NOT NULL is
the declared flag; a primary key does not necessarily set that flag in SQLite.
See [SQLite PRAGMA documentation](https://www.sqlite.org/pragma.html#pragma_table_xinfo).
Indexes, foreign keys and creation SQL are outside this first schema view.

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

## Scope

The common contracts and UI also compile for JS/Wasm. Room and synchronous
SQLite adapters in this tranche target Android/JVM/iOS; web hosts can implement
DatabaseSource. Runtime browser/database and physical-device smoke remain separate
from compilation and host tests.
