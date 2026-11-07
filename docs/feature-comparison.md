# Mothlight feature comparison and diagnostic priorities

Mothlight overlaps with existing Compose Multiplatform inspectors. The most useful
next work is deeper HTTP and database diagnosis, followed by reproducible evidence
export. Adding tabs alone does not establish usefulness or interface quality.

This comparison is dated November 7, 2026. Competitor capabilities below are
documented claims, not results of running their inspectors. **Unverified** means
the reviewed documentation does not establish the capability; it does not mean
the project lacks it. Mothlight entries describe this checkout.

## Functional comparison

| Capability | Mothlight | KmpInspector | Sidekick | AELog | KtorMonitor |
| --- | --- | --- | --- | --- | --- |
| HTTP capture | Ktor, OkHttp/Retrofit | Ktor, Android OkHttp | Ktor; other clients through store API | Ktor, OkHttp | Ktor, OkHttp, http4k |
| Request details | Headers, opt-in JSON tree/text, cURL, section copy actions, adaptive panes and status filters | Headers, text bodies, timing, sharing | Network inspector | Headers, JSON, status | Rich body previews and bulk export |
| App database tables | Searchable table picker, read-only grid, row details and bounded pages | Browse and edit | Browse and edit | Tables, schema, SQL | Unverified |
| Room integration | Separate Room 2 and Room 3 adapters | Room companion module | Room plugin | SQLiteDriver wrapper for Room | Unverified |
| Automatic table updates | InvalidationTracker, cell highlights and primary-key diffs on the current page | Open/Refresh documented | Unverified | Unverified | Unverified |
| Column schema | Types, declared NOT NULL, PK order, generated/hidden columns | Unverified | Unverified | Documented | Unverified |
| SQL execution trace | Driver wrapper, counts, total/last duration, errors | SQL editor; execution trace unverified | Read-only console; trace unverified | Driver interception and query logging | Unverified |
| Arbitrary SQL console | No | SELECT/WITH | Read-only | Read-only by default; optional writes | Unverified |
| Logs | Host-fed bounded feed; search, severity/tag filters, full messages, follow/pause and filtered copying | Logs; Android logcat integration | Logs and Kermit bridge | Logs | Unverified |
| Crash reports | No built-in collector | Fatal and nonfatal | Fatal and handled | Fatal and nonfatal | Unverified |
| Preferences and analytics | Host custom tools | Unverified | Typed preferences | Analytics plugin | Unverified |
| SSE and WebSocket capture | No | Unverified | Unverified | Unverified | Documented |
| Memory and startup | Memory overlay; explicit startup marks | Unverified | Unverified | Unverified | Unverified |
| App-owned extensions | Localized tabs and groups | Manual reporting APIs | Plugins and custom screens | Custom plugins | Unverified |

Sources: [KmpInspector README](https://github.com/waqas028/kmp-inspector#readme),
[Sidekick README](https://github.com/jipariz/sidekick#readme),
[AELog README](https://github.com/abdo-essam/AELog#readme), and
[KtorMonitor README](https://github.com/CosminMihuMDC/KtorMonitor#readme).
Database availability must be assessed per adapter: a web panel does not imply
that Room or native SQLite works in a browser. In particular, Sidekick documents
its database panel as unavailable on web targets.

## First addition

The database tool now has Data and Schema views. Schema metadata is available
through SQLite, Room 2 and Room 3 sources, with an explicit unsupported state for
custom sources that only implement rows. It shows declared types, the actual
SQLite NOT NULL flag, composite primary-key order and generated/hidden columns.
Defaults and generation expressions are not collected. See [database](database.md).

This closes a concrete gap for diagnosing migrations and unexpected constraints
without introducing arbitrary SQL execution, new dependencies or a global hook.
It does not yet include indexes, foreign keys, schema diffs or migration history.

## Priorities

These are proposed increments, not implemented modules or compatibility promises.
The order weighs diagnostic value, integration cost and the chance of misleading
the developer with incomplete evidence.

| Priority | Addition | Bug it helps investigate | Design condition |
| --- | --- | --- | --- |
| Next | HTTP comparison (the structured JSON viewer is now implemented) | Same endpoint returns different fields; repeated retries; wrong request payload | Compare recorded redacted values; distinguish omitted, truncated and absent fields; do not infer identical original requests from masked URLs |
| Next | Index and foreign-key metadata, then query plans | Missing index, wrong relationship, expensive table scan | Read-only generated queries; explicit explain action; never claim measured runtime from a plan |
| Next | Diagnostic export | A bug cannot be reproduced on the developer's device | Versioned bounded export of already-sanitized records; test redaction and omission reasons; host owns file/share integration |
| Later | Table snapshot comparison | Rows disappear or change unexpectedly after an action | Bounded, opt-in snapshots; match by actual key; preserve masking; make nontransactional reads explicit |
| Later | Timed operation spans | Startup marks cannot explain overlapping cache/database/network work | Host-owned start/end hooks; monotonic clock; unfinished/cancelled spans and bounded history |
| Later | UI responsiveness probe | UI freezes while network requests look normal | Separate platform probes; report observed stalls and measurement overhead; avoid claiming a root cause from a stall |
| Evaluate separately | Handled exception reports | Exceptions are caught but silently leave stale UI | Explicit host reporting first; review sensitive messages and bounded persistence before introducing fatal-process hooks |
| Host integration first | Coroutine and Flow observation | Collector remains active after leaving a screen; expected refresh never arrives | Explicit wrapper at the observed subscription; no inference from repeated SQL; require a real consumer scenario before adding a library module |

Keep preferences, feature flags, WorkManager and analytics as host tools until
multiple consuming apps justify reusable adapters. They can be useful, but do not
automatically belong in the portable core. Network replay and database editing
change app behavior; prioritize observation and evidence before those controls.

Previously removed lifecycle, navigation, realtime, invariant and retention tools
remain removed. Any future work in those areas needs a new scoped design and
validation against actual consumer needs; this comparison does not restore them.

## Interface evaluation

Evaluate working inspectors in this order: KtorMonitor for HTTP details, AELog for
its combined tools, KmpInspector for database workflows, Sidekick for its shell
and plugin navigation. Include Inspektify as a focused network reference
([repository](https://github.com/BVantur/inspektify)).

Use one synthetic dataset and identical tasks. Record platform, version, window
size and light/dark theme for each run. Screenshots alone do not measure usability.

1. Find a failed request among 200 successful requests and identify the error.
2. Compare two calls to the same endpoint with different redacted JSON responses.
3. Read a long payload, find a nested value and copy useful evidence.
4. Open a wide table, find a row, inspect its schema and observe a DAO update.
5. Follow repeated slow SQL and distinguish statement duration from CPU time.
6. Close and reopen the panel while continuing to use the host application.
7. Repeat on a narrow window, with keyboard navigation and a long localized label.

For each task record completion, wrong turns, interactions, elapsed time and lost
context. Check focus order, selection/copy, readable empty/error/loading states,
contrast, truncation and live-update scroll behavior. Use findings to choose the
Mothlight layout rather than copying an entire competitor screen.

The target is a developer finding the cause faster: visible status and duration,
predictable list/detail navigation, preserved filters and selection, structured
payloads, and copy actions at the point of use. Claims of being more convenient
require those task results. Visual polish must also be checked in actual Android,
iOS and desktop rendering; JVM compilation alone cannot establish it.
