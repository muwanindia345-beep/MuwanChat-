package com.muwan.muwanchat.network

// SEC_FIX_V1
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkRetryInterceptor(
    private val maxRetries: Int = 2,
    private val retryDelayMs: Long = 600L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // GET/HEAD dobara bhejna safe hai. POST/PUT/DELETE tab hi retry jab request server tak
        // pahunchi hi nahi (connect/DNS fail) -- warna message/upload double ho sakta hai.
        val idempotent = request.method == "GET" || request.method == "HEAD"
        var lastException: IOException? = null

        for (attempt in 0..maxRetries) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                lastException = e
                // User ne call cancel ki ho to retry nahi
                if (chain.call().isCanceled()) throw e
                val neverSent = e is ConnectException || e is UnknownHostException ||
                    (e is SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) == true)
                if (!(idempotent || neverSent) || attempt >= maxRetries) throw e
                try {
                    Thread.sleep(retryDelayMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("retry interrupted")
                }
            }
        }
        throw lastException ?: IOException("Network request failed after $maxRetries retries")
    }
}
