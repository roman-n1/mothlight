package io.github.mothlight.sample

import io.github.mothlight.*

/** Synthetic scenario with deterministic times; no real lifecycle or navigation action occurs. */
fun diagnosticScenario(mothlight: Mothlight) {
    mothlight.startup.clear()
    mothlight.startup.mark("database_opened", 100)
    mothlight.startup.mark("graph_ready", 150)
    mothlight.startup.mark("first_frame", 300)
    mothlight.lifecycle.record("DemoScreen", LifecycleEvent.CREATED, 100)
    mothlight.lifecycle.record("DemoScreen", LifecycleEvent.CREATED, 200)
    mothlight.lifecycle.record("DemoScreen", LifecycleEvent.DESTROYED, 300)
    mothlight.navigation.record("Home", NavigationDirection.RESET, 100)
    mothlight.navigation.record("Details", NavigationDirection.PUSH, 200)
    mothlight.invariants.record("demo.sorted", InvariantSeverity.ERROR, 250,
        sampleJson = """{"index":3,"token":"demo-secret"}""", screen = ScreenContext.Named("Details"))
    mothlight.navigation.record("Home", NavigationDirection.POP, 300)
    mothlight.invariants.record("demo.sorted", InvariantSeverity.WARNING, 350,
        sampleJson = """{"index":1,"token":"demo-secret"}""", screen = ScreenContext.Named("Home"))
}
