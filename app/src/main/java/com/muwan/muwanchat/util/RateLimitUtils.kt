package com.muwan.muwanchat.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Error text for a failed (non-2xx) auth call.
 *
 * Retrofit gives res.body() == null on 401/429/500, so the server's message
 * lives in errorBody(). 429 -> "Your account is rate limited. Resets in 1h 23m (at 8:45 PM)".
 * Reset time is read from Retry-After / RateLimit-Reset / X-RateLimit-Reset
 * headers, or retryAfter / resetAt style fields in the JSON body.
 */
fun authErrorMessage(res: Response<*>, fallback: String): String {
    val raw = try { res.errorBody()?.string() } catch (e: Exception) { null }
    val json: JsonObject? = try {
        if (raw.isNullOrBlank()) null else JsonParser().parse(raw).asJsonObject
    } catch (e: Exception) { null }

    if (res.code() == 429) {
        val secs = rateLimitResetSeconds(res, json)
        return if (secs != null) {
            val at = SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(Date(System.currentTimeMillis() + secs * 1000))
            "Your account is rate limited. Resets in ${formatDuration(secs)} (at $at)"
        } else {
            "Your account is rate limited. Please try again later."
        }
    }
    return json?.str("error") ?: json?.str("message") ?: fallback
}

private fun JsonObject.str(key: String): String? =
    if (has(key) && get(key).isJsonPrimitive) get(key).asString else null

private fun JsonObject.number(key: String): Double? =
    if (has(key) && get(key).isJsonPrimitive) {
        try { get(key).asDouble } catch (e: Exception) { null }
    } else null

// Value can be "seconds from now", epoch seconds or epoch millis.
private fun toSecondsFromNow(v: Double, nowMs: Long): Long = when {
    v > 1e12 -> ((v - nowMs) / 1000).toLong()
    v > 1e9  -> (v - nowMs / 1000).toLong()
    else     -> v.toLong()
}.coerceAtLeast(0)

private fun parseDate(v: String, pattern: String, utc: Boolean): Long? = try {
    val f = SimpleDateFormat(pattern, Locale.US)
    if (utc) f.timeZone = TimeZone.getTimeZone("UTC")
    f.parse(v)?.time
} catch (e: Exception) { null }

private fun rateLimitResetSeconds(res: Response<*>, json: JsonObject?): Long? {
    val now = System.currentTimeMillis()
    val h = res.headers()

    h["Retry-After"]?.trim()?.let { v ->
        v.toDoubleOrNull()?.let { return toSecondsFromNow(it, now) }
        parseDate(v, "EEE, dd MMM yyyy HH:mm:ss zzz", false)
            ?.let { return ((it - now) / 1000).coerceAtLeast(0) }
    }
    for (name in listOf("RateLimit-Reset", "X-RateLimit-Reset")) {
        h[name]?.trim()?.toDoubleOrNull()?.let { return toSecondsFromNow(it, now) }
    }
    if (json != null) {
        for (k in listOf("retryAfter", "retry_after", "resetIn", "resetAfter",
                         "resetAt", "resetTime", "reset")) {
            json.number(k)?.let { return toSecondsFromNow(it, now) }
            json.str(k)?.let { s ->
                parseDate(s, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", true)
                    ?.let { return ((it - now) / 1000).coerceAtLeast(0) }
            }
        }
    }
    return null
}

private fun formatDuration(secs: Long): String {
    val totalMin = (secs + 59) / 60
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        totalMin < 1 -> "less than a minute"
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
