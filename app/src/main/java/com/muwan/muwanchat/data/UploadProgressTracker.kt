package com.muwan.muwanchat.data

import androidx.compose.runtime.mutableStateMapOf

// Real-time upload % track karne ke liye — messageId -> 0..100
// mutableStateMapOf hone ki wajah se MessageBubble automatically recompose
// hoga jaise jaise % change hota hai, koi manual callback/flow chahiye nahi.
object UploadProgressTracker {
    val progress = mutableStateMapOf<String, Int>()

    fun start(id: String) {
        progress[id] = 0
    }

    fun update(id: String, percent: Int) {
        progress[id] = percent.coerceIn(0, 100)
    }

    fun clear(id: String) {
        progress.remove(id)
    }
}
