package com.muwan.muwanchat.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class RetryingDns(
    private val maxAttempts: Int = 3,
    private val retryDelayMs: Long = 400L
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        var lastError: UnknownHostException? = null

        for (attempt in 1..maxAttempts) {
            try {
                return Dns.SYSTEM.lookup(hostname)
            } catch (e: UnknownHostException) {
                lastError = e
                if (attempt < maxAttempts) {
                    Thread.sleep(retryDelayMs)
                }
            }
        }
        throw lastError ?: UnknownHostException(hostname)
    }
}
