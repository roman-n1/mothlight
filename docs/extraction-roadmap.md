# Extraction inventory

This inventory comes from implemented tools in the original Debug panel, not
from its aspirational backlog. It separates shipped reusable tools from future
extractions and host-specific plugins. No original application source is needed
to build Mothlight.

| Original capability | Mothlight status | Remaining work |
| --- | --- | --- |
| HTTP transactions, request details, cURL | Available; Ktor and OkHttp/Retrofit | Explicit replay UI |
| Logs and generic diagnostic events | Available | Category-specific drill-down |
| Room table browser and invalidations | Available; Room 2/3 adapters | Schema details, optional controlled read-only SQL console |
| SQL execution feed and query statistics | Available; SQLiteDriver wrapper | Query plans; separately labelled subscription heuristic |
| Socket frames and connection history | Available; host-instrumented passive hooks | Optional transport adapters, not app-specific channel enums |
| Startup phases and navigation timing | Generic events can carry them | Typed spans, waterfall and timing summaries |
| Lifecycle counters and component trees | Generic events can carry them | Owner-supplied tree snapshots, churn analysis |
| Navigation breadcrumbs | Generic events can carry them | Typed bounded trail and filtering |
| Invariant counters and alarms | Generic events can carry them | Severity, counters and detail view |
| Capability and subscription diagnostics | Custom tool/source API | Typed portable contracts |
| DI graph, singleton identity diagnostics | Custom tool/source API | Graph source contract and viewer without global registry |
| Network latency/error simulation | Not yet extracted | Explicit opt-in adapter and deterministic rule tests |
| Flight recorder and timeline export | Not yet extracted | Bounded unified capture, redacted export format |
| UI switches, crash triggers | Host plugin | Actions remain explicitly owned by the application |
| Sessions, quick login, outbox, message seeder, call tools | Host plugin | Keep product models and actions outside the library |

Start with observation and bounded sources. Add deliberate actions (replay,
network disruption, crash triggers) through explicit tools, not hidden side effects
of opening a panel. Nothing in the table marked remaining is claimed as implemented.
