# Startup, lifecycle, navigation and invariants

These four tools are included in `defaultMothlightTools`. Their typed recorders
belong to each Mothlight instance and have the same recording gate as HTTP/SQL.
No navigation framework, lifecycle library, DI container or platform-specific
runtime is required. Android Views consumers see the same tools through
`createMothlightView` without Compose source code.

Create Mothlight early and enable recording before the activity you want to
observe. The examples below use explicit timestamps so production clocks and
deterministic tests share the same entry points. Recording works with the panel
closed; closing a tab cancels its UI collector. `clear()` clears all four histories
and counters, but never resets event IDs or changes the recording gate.

## Startup milestones

```kotlin
mothlight.startup.mark("database_opened", 100)
mothlight.startup.mark("graph_ready", 150)
mothlight.startup.mark("first_frame", 300)
```

The panel shows a waterfall of intervals: 50 ms, 150 ms, then 0 ms at the final
milestone. These are gaps between milestones, not measured CPU work or arbitrary
nested spans. Supply one clock and origin for all timestamps. Duplicate stage
names and equal timestamps are allowed and keep distinct IDs. Out-of-order
reports are sorted by time, with IDs as the tie-breaker. When capacity is reached,
the oldest timestamps are evicted and retained intervals are recalculated.
Offsets in the UI are relative to the first retained milestone.

Start a new measurement by calling `mothlight.startup.clear()`. There is no
implicit "process start" or first-frame detection; the host owns those hooks.

## Lifecycle observations

```kotlin
mothlight.lifecycle.record("DetailsScreen", LifecycleEvent.CREATED, nowMillis)
// At the lifecycle boundary owned by your component/store:
mothlight.lifecycle.record("DetailsScreen", LifecycleEvent.DESTROYED, laterMillis)
```

The tool has counters and an event history, filtered by component kind. Its
balance is observed creations minus observed destructions. It can be negative
when observation begins after creation, pauses, or counters are reset/evicted.
A positive balance is not proof of a leak. Object identity, expected retained
instances and a component-tree source would be needed for that conclusion.

Feed capacity and distinct-kind capacity are separate. Counters outlive event
feed eviction; the least recently reported kind is evicted at the kind limit.
The recorder does not retain component instances, subscribe to lifecycle owners
or discover objects with reflection. The host must report both boundaries.

## Navigation breadcrumbs

```kotlin
mothlight.navigation.record("Home", NavigationDirection.RESET, nowMillis)
mothlight.navigation.record("Details", NavigationDirection.PUSH, laterMillis)
mothlight.navigation.record("Home", NavigationDirection.POP, laterStillMillis)
```

`REPLACE` is also supported. This is a bounded transition history, not a navigation
stack implementation. Each report receives a unique ID, including repeated visits
to the same destination. The panel filters route labels and shows newest retained
reports first. Supply static, sanitized route labels; do not pass serialized
configurations, URLs with arguments, tokens or personal information. There is no
automatic coupling to Decompose, Jetpack Navigation or the invariant recorder.

## Invariant observations

```kotlin
mothlight.invariants.record(
    rule = "items.sorted",
    severity = InvariantSeverity.WARNING,
    timestampMillis = nowMillis,
    sampleJson = """{"index":3,"token":"private"}""",
    screen = "Details",
)
```

Severity can be INFO, WARNING or ERROR. Reporting does not enforce a rule, throw
because of a violation, crash the app or promote warnings to assertions. The tool
shows count, highest observed severity, last severity/time, sample and explicit
screen context. A later INFO does not erase an earlier ERROR. Counters survive
feed eviction and are themselves bounded by a least-recently-reported rule limit.
The event flow remains available for host-specific timelines and exports.

Samples are omitted by default. With `Redactor(captureJsonBodies = true)`, JSON
is masked before entering either counters or history; non-JSON and oversized
samples are omitted. Pass extra sensitive keys through Redactor for your schema.
Rule IDs and screen names must be safe static labels. Labels are capped at 256
characters; use short distinct labels to avoid truncation collisions. Startup
stage, lifecycle kind and navigation destination labels have the same limit.

The screen is a snapshot supplied at report time. Later navigation does not
rewrite it. The library does not read another recorder's mutable state to infer
context or capture application/domain objects.

## What remains separate

Component tree inspection and churn analysis, arbitrary timed spans, automatic
platform/framework hooks and unified flight-recorder export are not part of this
tranche. The public snapshots and custom tool API are available to integrate
host-owned sources without a global registry.
