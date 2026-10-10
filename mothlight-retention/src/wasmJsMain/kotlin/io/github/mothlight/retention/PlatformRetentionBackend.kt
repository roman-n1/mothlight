package io.github.mothlight.retention

actual fun platformRetentionBackend(): RetentionBackend = object : RetentionBackend {
    override val support = RetentionSupport.UNSUPPORTED
    override fun reference(target: Any): WeakHandle = error("Retention observation is unsupported on this platform")
    override fun requestCollection() = Unit
}
