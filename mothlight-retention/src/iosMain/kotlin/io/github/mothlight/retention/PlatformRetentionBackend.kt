package io.github.mothlight.retention

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

@OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class)
actual fun platformRetentionBackend(): RetentionBackend = object : RetentionBackend {
    override val support = RetentionSupport.KOTLIN_NATIVE
    override fun reference(target: Any): WeakHandle {
        val weak = WeakReference(target)
        return WeakHandle { weak.get() != null }
    }
    override fun requestCollection() { GC.collect() }
}
