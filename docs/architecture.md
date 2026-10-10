# Architecture and extraction

Mothlight is an instance-owned diagnostics toolkit. A host owns the `Mothlight`
instance, recording policy, visibility and application-specific tools. The panel
never installs global lifecycle hooks or owns login/logout.

```text
application / debug source set
  ├── Mothlight instance
  ├── Ktor or OkHttp adapter → HTTP recorder → redactor → bounded StateFlow
  ├── host log and event adapters → bounded feeds
  └── Compose panel or Android View adapter → selected tool only
```

## Origin

The following pieces were extracted or adapted from the original Debug panel:

| Original area | Mothlight |
| --- | --- |
| `core/debug-probe/ProbeFeed` | Instance-gated bounded feed; global gate removed |
| `core/network/.../NetworkTransaction` | HTTP transaction and status model |
| `core/network/.../NetworkInspector` | Atomic IDs, bounded records, matching completion updates |
| `core/debug-menu/CurlCommandBuilder` | POSIX cURL rendering and original regression tests |
| `core/network/.../MessengerHttpInspector` | Ktor send/response observation, now instance-bound |
| `core/debug-menu` grouped tools | New standalone Compose host and explicit custom-tool API |

The original application's source tree and history are not copied into this repo.
Package names, build conventions, privacy policy and UI integration were changed.

## Boundaries

- Core knows neither Compose nor Ktor, OkHttp, Retrofit, Decompose, Room or the host DI.
- A headless integration depends on the core plus its chosen transport adapter.
- The panel uses Material 3 and owns its localized resources.
- Navigation, authentication, outbox, seeding, call control and business-domain
  inspectors stay in the application. A host may expose them as custom tools.
- Database, socket and navigation-specific bundled plugins are follow-up work;
  the custom-tool and diagnostic-event APIs already accept host-owned integrations.

## Migrating an existing panel

1. Own one Mothlight instance in the application graph.
2. Connect the HTTP adapter to the existing client and bridge logs/events explicitly.
3. Replace the debug host with the standalone panel; retain application tools as
   custom tabs using their existing ports and lifecycle owners.
4. Compare recording, masking, enable/disable, navigation and logout behavior on
   each target before removing the previous implementation.

The originating application has not yet switched to this library. Its existing
Debug panel remains intact while the standalone alpha is validated.

## Contract tests

- Disabled gate → record → no retained item; enable/disable → only enabled events.
- Capacity two → record three values → exact newest two, in order.
- Two instances → different gates/records → no shared mutable state.
- Clear or evict before HTTP completion → no resurrected transaction.
- Concurrent starts → unique IDs and independent completion updates.
- Mixed-case headers and nested JSON → secrets masked before StateFlow publication.
- Ktor and Retrofit production entry points → original caller response preserved.
- One-shot request body → exactly one transport write.
- Tool IDs → duplicate/blank/empty collections rejected.
- EN/RU resource keys and formatting arguments → matching complete sets.
