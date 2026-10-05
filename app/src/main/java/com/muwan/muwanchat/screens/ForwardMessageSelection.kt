package com.muwan.muwanchat.screens

object ForwardMessageSelection {
    var messages: List<ChatMessage> = emptyList()

    // true = channel invite jaisa message, "Forwarded" label ke bina bhejo.
    // set() hamesha false karta hai (normal forward), invite flow set() ke
    // BAAD ise true karta hai. clear() bhi reset karta hai.
    var sendAsOriginal: Boolean = false

    fun set(list: List<ChatMessage>) {
        messages = list
        sendAsOriginal = false
    }

    fun clear() {
        messages = emptyList()
        sendAsOriginal = false
    }
}
