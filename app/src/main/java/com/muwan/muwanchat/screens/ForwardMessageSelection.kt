package com.muwan.muwanchat.screens

object ForwardMessageSelection {
    var messages: List<ChatMessage> = emptyList()

    fun set(list: List<ChatMessage>) {
        messages = list
    }

    fun clear() {
        messages = emptyList()
    }
}
