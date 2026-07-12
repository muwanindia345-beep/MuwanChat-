package com.muwan.muwanchat.screens

import androidx.compose.runtime.mutableStateListOf
import com.muwan.muwanchat.network.UserItem

object GroupMemberSelection {
    val selected = mutableStateListOf<UserItem>()

    fun isSelected(uid: String): Boolean = selected.any { it.uid == uid }

    fun toggle(user: UserItem) {
        val existing = selected.indexOfFirst { it.uid == user.uid }
        if (existing >= 0) selected.removeAt(existing) else selected.add(user)
    }

    fun remove(uid: String) {
        selected.removeAll { it.uid == uid }
    }

    fun clear() {
        selected.clear()
    }
}
