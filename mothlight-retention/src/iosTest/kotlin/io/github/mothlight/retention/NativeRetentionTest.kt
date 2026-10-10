package io.github.mothlight.retention

import kotlin.test.*

class NativeRetentionTest {
    private class Target(val value: Int)
    private fun releasedHandle(backend: RetentionBackend): WeakHandle = backend.reference(Target(1))

    @Test fun nativeBackendDistinguishesReleasedObjectFromStronglyRootedControl() {
        val backend = platformRetentionBackend()
        assertEquals(RetentionSupport.KOTLIN_NATIVE, backend.support)
        val root = Target(42)
        val live = backend.reference(root)
        val released = releasedHandle(backend)
        // GC.collect waits for a complete Kotlin/Native collection; no timing/polling assertion.
        backend.requestCollection()
        assertFalse(released.isAlive())
        assertTrue(live.isAlive())
        assertEquals(42, root.value)
    }
}
