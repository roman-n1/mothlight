# Extraction inventory

Mothlight keeps portable HTTP, database, log, startup and memory-usage tools.
The original application's source is not needed to build it.

| Capability | Status | Remaining work |
| --- | --- | --- |
| HTTP transactions, details and cURL | Ktor and OkHttp/Retrofit; adaptive list/details, filters, JSON tree, text and copy actions | Request comparison, explicit replay UI |
| Logs | Instance-owned bounded feed; search, severity/tag filters, expandable messages, follow/pause and copy actions | Structured payload viewers and trace links |
| Room table browser and invalidations | Room 2/3 adapters; column schema with constraints and generated kinds | Indexes, foreign keys, optional read-only SQL console |
| SQL execution feed and statistics | SQLiteDriver wrapper | Query plans |
| Startup milestones | Ordered milestones and interval waterfall | Arbitrary timed spans |
| Memory usage overlay | Heap/native/process metrics where supported | Host-owned persistence and platform readers |
| Custom tools | Localized tabs and grouped subtools | Host supplies its own screens and controllers |

Realtime transport capture, lifecycle counters, navigation history, invariant
reporting, generic diagnostic events, retained-object watching, heap analysis and
demo scenarios have been removed from this library. Their implementations,
recorders, dependencies and sample hooks are removed along with their tabs.

Decompose trees, Centrifuge-specific tools, DI graphs, authentication, outbox,
message seeding and call controls belong to host extensions. Independent libraries
such as Actron can supply their existing Compose screens through grouped tools.

See [feature comparison and diagnostic priorities](feature-comparison.md) for
documented alternatives, proposed additions and the interface evaluation tasks.
