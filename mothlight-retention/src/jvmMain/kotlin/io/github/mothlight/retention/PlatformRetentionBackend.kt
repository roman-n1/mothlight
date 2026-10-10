package io.github.mothlight.retention

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

actual fun platformRetentionBackend(): RetentionBackend = object : RetentionBackend {
    override val support = RetentionSupport.MANAGED_HEAP
    override fun reference(target: Any): WeakHandle = QueueHandle(target, WatchKey("unkeyed", 0))
    override fun reference(target: Any, key: WatchKey): WeakHandle = QueueHandle(target, key)
    override fun requestCollection() { Runtime.getRuntime().gc() }
}

private class QueueHandle(target: Any, key: WatchKey) : WeakHandle {
    private val queue = ReferenceQueue<Any>()
    private val reference = HeapWatchReference(target, queue, key.sessionId, key.watchId)
    @Volatile private var collected = false
    override fun isAlive(): Boolean {
        // Read the queue, never Reference.get(): a temporary strong reference can perturb a GC check.
        // Identity comparison also keeps the weak-reference object itself alive until it is enqueued.
        if (queue.poll() === reference) collected = true
        return !collected
    }
}

/** Stable heap-dump schema. The analyzer reads these fields in HPROF, never through reflection. */
internal class HeapWatchReference(
    target: Any,
    queue: ReferenceQueue<Any>,
    val sessionId: String,
    val watchId: Long,
) : WeakReference<Any>(target, queue)
