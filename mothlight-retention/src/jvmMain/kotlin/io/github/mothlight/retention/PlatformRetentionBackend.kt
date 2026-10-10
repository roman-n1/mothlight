package io.github.mothlight.retention

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

actual fun platformRetentionBackend(): RetentionBackend = object : RetentionBackend {
    override val support = RetentionSupport.MANAGED_HEAP
    override fun reference(target: Any): WeakHandle = QueueHandle(target)
    override fun requestCollection() { Runtime.getRuntime().gc() }
}

private class QueueHandle(target: Any) : WeakHandle {
    private val queue = ReferenceQueue<Any>()
    private val reference = WeakReference(target, queue)
    @Volatile private var collected = false
    override fun isAlive(): Boolean {
        // Read the queue, never Reference.get(): a temporary strong reference can perturb a GC check.
        // Identity comparison also keeps the weak-reference object itself alive until it is enqueued.
        if (queue.poll() === reference) collected = true
        return !collected
    }
}
