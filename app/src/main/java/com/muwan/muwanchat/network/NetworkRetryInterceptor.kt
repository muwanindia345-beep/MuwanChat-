package com.muwan.muwanchat.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class NetworkRetryInterceptor(
    private val maxRetries: Int = 2,
    private val retryDelayMs: Long = 600L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: IOException? = null

        for (attempt in 0..maxRetries) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    Thread.sleep(retryDelayMs)
                }
            }
        }
        throw lastException ?: IOException("Network request failed after $maxRetries retries")
    }
}
