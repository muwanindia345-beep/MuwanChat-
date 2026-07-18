package com.muwan.muwanchat.screens

object AvatarViewerSelection {
    var avatarBase64: String? = null
    var title: String = ""

    fun set(avatarBase64: String?, title: String) {
        this.avatarBase64 = avatarBase64
        this.title = title
    }

    fun clear() {
        avatarBase64 = null
        title = ""
    }
}
