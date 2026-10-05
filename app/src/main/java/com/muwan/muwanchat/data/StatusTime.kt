package com.muwan.muwanchat.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Status ka real time label (WhatsApp jaisa). 24 ghante TTL hai, isliye sirf
// "minutes ago" / "Today" / "Yesterday" aate hain.
object StatusTime {

    fun label(createdAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diffMin = ((nowMs - createdAtMs) / 60_000L).coerceAtLeast(0L)
        if (diffMin < 1L) return "Just now"
        if (diffMin < 60L) return if (diffMin == 1L) "1 minute ago" else "$diffMin minutes ago"

        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(createdAtMs))
        val dayDiff = Math.round((startOfDay(nowMs) - startOfDay(createdAtMs)) / 86_400_000.0).toInt()
        return when {
            dayDiff <= 0 -> "Today, $time"
            dayDiff == 1 -> "Yesterday, $time"
            else -> time
        }
    }

    private fun startOfDay(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
