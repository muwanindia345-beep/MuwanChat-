#!/data/data/com.termux/files/usr/bin/bash
python3 << 'PYEOF'

import io

def patch_file(path):
    with io.open(path, encoding='utf-8', newline='') as f:
        c = f.read()

    old_state = """    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    var fullscreenVideo by remember { mutableStateOf<String?>(null) }"""
    new_state = """    var fullscreenImage by remember { mutableStateOf<ChatMessage?>(null) }
    var fullscreenVideo by remember { mutableStateOf<ChatMessage?>(null) }"""
    if old_state not in c:
        print("STATE pattern not found in " + path + "!"); raise SystemExit(1)
    c = c.replace(old_state, new_state)

    old_tap = """                        onImageTap = { url -> fullscreenImage = url },
                        onVideoTap = { url -> fullscreenVideo = url },"""
    new_tap = """                        onImageTap = { _ -> fullscreenImage = msg },
                        onVideoTap = { _ -> fullscreenVideo = msg },"""
    if old_tap not in c:
        print("TAP pattern not found in " + path + "!"); raise SystemExit(1)
    c = c.replace(old_tap, new_tap)

    old_render = """    fullscreenImage?.let { url ->
        FullscreenImageViewer(model = url, onDismiss = { fullscreenImage = null })
    }

    fullscreenVideo?.let { url ->
        FullscreenVideoPlayer(url = url, onDismiss = { fullscreenVideo = null })
    }"""
    new_render = """    fullscreenImage?.let { mediaMsg ->
        FullscreenImageViewer(
            model = mediaMsg.mediaUrl ?: "",
            onDismiss = { fullscreenImage = null },
            onSendReply = { replyText ->
                sendMessageWithId(UUID.randomUUID().toString(), replyText, nowIso(), isRetry = false, replyToId = mediaMsg.id)
                fullscreenImage = null
            }
        )
    }

    fullscreenVideo?.let { mediaMsg ->
        FullscreenVideoPlayer(
            url = mediaMsg.mediaUrl ?: "",
            onDismiss = { fullscreenVideo = null },
            onSendReply = { replyText ->
                sendMessageWithId(UUID.randomUUID().toString(), replyText, nowIso(), isRetry = false, replyToId = mediaMsg.id)
                fullscreenVideo = null
            }
        )
    }"""
    if old_render not in c:
        print("RENDER pattern not found in " + path + "!"); raise SystemExit(1)
    c = c.replace(old_render, new_render)

    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(c)
    print("Patched " + path)

patch_file("app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt")
patch_file("app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt")

PYEOF
