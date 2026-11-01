package com.muwan.muwanchat.network

// SEC_FIX_V1
// Bearer token sirf apne backend ko jaana chahiye. Media URL message ke content se aata hai
// (sender ka diya hua), isliye kisi bhi URL par token chipkana token-leak hai.
// Agar kabhi media dusre host se serve ho aur token chahiye ho to host yahan TRUSTED_EXTRA_HOSTS mein daalo.
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object BackendHost {

    private val TRUSTED_EXTRA_HOSTS: Set<String> = emptySet()

    private val backend: HttpUrl? = com.muwan.muwanchat.BuildConfig.BACKEND_URL.toHttpUrlOrNull()

    fun isBackend(url: HttpUrl): Boolean {
        val b = backend ?: return false
        if (url.scheme != b.scheme || url.port != b.port) return false
        return url.host.equals(b.host, ignoreCase = true) ||
            TRUSTED_EXTRA_HOSTS.any { it.equals(url.host, ignoreCase = true) }
    }

    fun isBackend(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val parsed = url.toHttpUrlOrNull() ?: return false
        return isBackend(parsed)
    }
}
