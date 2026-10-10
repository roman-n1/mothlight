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
| Startup phases and navigation timing | Typed startup milestones and interval waterfall available | Arbitrary timed spans and navigation timing |
| Lifecycle counters | Typed counters and lifecycle history available | Host reports creation/destruction; no framework required |
| Decompose component tree | Host plugin; excluded from the standard library | Keep framework object graphs and tree traversal in the host |
| Navigation breadcrumbs | Typed bounded trail with direction and filtering available | Framework hooks belong to host integrations |
| Invariant counters and alarms | Counts, peak/last severity, masked samples and screen context available | Host-owned alert policy |
| Capability and subscription diagnostics | Custom tool/source API | Typed portable contracts |
| DI graph, singleton identity diagnostics | Host plugin | Concrete DI instrumentation stays in the host |
| Network latency/error simulation | Not yet extracted | Explicit opt-in adapter and deterministic rule tests |
| Flight recorder and timeline export | Not yet extracted | Bounded unified capture, redacted export format |
| Memory usage setting and overlay | Live heap/native/process metrics; Compose and Android Views | Host-owned persistence; heap dumps remain separate |
| Retained objects / leak indicator | [Portable watcher, tool and live badge](retention.md) available on Android/JVM/iOS | Optional Shark/native heap analysis; no confirmed leak verdict yet |
| UI switches, crash triggers | Host plugin | Actions remain explicitly owned by the application |
| Sessions, quick login, outbox, message seeder, call tools | Host plugin | Keep product models and actions outside the library |

Start with observation and bounded sources. Add deliberate actions (replay,
network disruption, crash triggers) through explicit tools, not hidden side effects
of opening a panel. Nothing in the table marked remaining is claimed as implemented.
