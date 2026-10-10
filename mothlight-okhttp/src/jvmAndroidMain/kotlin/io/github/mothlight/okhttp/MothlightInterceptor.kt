package io.github.mothlight.okhttp

import io.github.mothlight.NetworkInspector
import okhttp3.Interceptor
import okhttp3.Response

/** Application interceptor for OkHttp and Retrofit. */
class MothlightInterceptor(private val inspector: NetworkInspector) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
