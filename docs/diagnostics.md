# Startup milestones

The startup recorder belongs to each Mothlight instance and follows its recording
gate. The host supplies timestamps and milestones; the panel shows their intervals.
Closing the panel stops its UI collector without stopping recording.
`mothlight.clear()` also clears the startup history.

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
