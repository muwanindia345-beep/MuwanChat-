# Fix: GIF/sticker/meme messages normal photo jaisa bordered/background chat
# bubble mein "cover" ho jaate the — dedicated sticker-style bubble nahi milta
# tha jo WhatsApp/Telegram jaisa borderless, direct-render hota hai.
#
# ROOT CAUSE:
#   MessageBubble.kt mein already ek POORA-BANA-HUA sticker/gif render path hai:
#     val isSticker = message.type == "gif"
#   Yeh borderless bubble (no orange/green background, no padding), bada size
#   (140-180dp), ContentScale.Fit (poora gif load, crop nahi) aur corner-overlay
#   timestamp deta hai — bilkul jaisa dusre apps mein sticker dikhta hai.
#
#   Lekin yeh code kabhi chalta hi nahi tha, kyunki GIF upload karte waqt
#   category/type hamesha "image" bheja jaata tha (ek purana workaround, jab
#   backend "gif" category accept nahi karta tha). Maine backend source
#   (muwan-chat-backend-main) check kiya — backend AB "gif" ko as a proper
#   category fully support karta hai:
#     - routes/chat.js: ['image','document','audio','gif'].includes(category)
#     - gif ke liye mime whitelist: image/gif, image/webp
#     - data ko as-is (raw base64) store karta hai — koi re-compress/resize
#       nahi hota, animation frames safe rehte hain
#     - message "type" field bhi kisi whitelist se restrict nahi hai
#       (type: type || 'text' — sirf empty hone par text banta hai)
#   Matlab backend ka woh purana limitation ab exist hi nahi karta — client
#   side ka workaround purana pad gaya tha, backend update hone ke baad wapas
#   fix nahi hua.
#
# FIX:
#   GIF upload calls (1:1 chat + group chat) mein category "image" ki jagah
#   "gif" bhejo. Isse turant unlock ho jaata hai:
#     - MessageBubble ka already-built borderless sticker bubble
#     - Poora gif load (Fit) bajaye photo jaisa crop ke
#   Aur conversation-list preview (ChatRepository.kt) mein "gif" ka case add
#   kiya taaki "🎬 GIF" dikhe, raw URL text nahi (pehle "gif" case exist hi
#   nahi karta tha kyunki type kabhi "gif" set hi nahi hota tha).
#
# NOTE: Yeh patch pichle "patch_fix_upload_cancel_on_navigate_away.py" ke
#   saath ya usके bina — dono state mein chalega (khud detect karega).
#
# Termux mein repo root (jahan app/ folder hai) se run karo:
#   python patch_fix_gif_sticker_bubble.py

import os

# ── 0) UploadScope.kt zaroor exist kare (agar pichla navigation-cancel patch
#      nahi chala tha to yahi bana degा) — GIF upload calls neeche isी scope
#      ka reference karte hain, isliye yeh file + import dono jagah honi
#      chahiye chahe pichla patch chala ho ya na ho.
upload_scope_path = "app/src/main/java/com/muwan/muwanchat/data/UploadScope.kt"
if not os.path.exists(upload_scope_path):
    os.makedirs(os.path.dirname(upload_scope_path), exist_ok=True)
    with open(upload_scope_path, "w") as f:
        f.write('''package com.muwan.muwanchat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Media uploads (photo/video/document/music/voice/gif) ke liye app-lifetime
// scope. rememberCoroutineScope() (Compose) ke bajaye isko use karo taaki user
// chat screen se navigate away ho jaaye (ya app background chala jaaye) to bhi
// upload background mein chalta rahe aur poora complete ho jaaye.
object UploadScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
''')
    print(f"CREATED: {upload_scope_path}")

def ensure_import(path, import_line, anchor):
    s = open(path).read()
    if import_line in s:
        return
    assert anchor in s, f"import anchor not found in {path}"
    s = s.replace(anchor, anchor + "\n" + import_line, 1)
    with open(path, "w") as f:
        f.write(s)
    print(f"PATCHED: added import to {path}")

def patch_file(path, replacements):
    s = open(path).read()
    orig = s
    for candidates, new in replacements:
        # candidates: list of possible old-strings (try in order — pehla
        # jo file mein mile use karo). Isse yeh patch chalega chahe pichla
        # navigation-cancel patch already apply ho chuka ho ya na ho.
        matched = None
        for old in candidates:
            if s.count(old) == 1:
                matched = old
                break
            elif s.count(old) > 1:
                raise AssertionError(f"multiple matches in {path} for:\n{old}")
        if matched is None:
            raise AssertionError(f"no candidate matched in {path}; file may already be patched or changed unexpectedly")
        s = s.replace(matched, new, 1)

    if s == orig:
        print(f"NO CHANGE: {path}")
    else:
        with open(path, "w") as f:
            f.write(s)
        print(f"PATCHED: {path}")


# ── 1) ChatScreen.kt (1:1 chat) — onGifReceived ─────────────────────────────
chat_screen = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
ensure_import(chat_screen, "import com.muwan.muwanchat.data.UploadScope", "import com.muwan.muwanchat.data.UploadProgressTracker")
patch_file(chat_screen, [
    (
        [
            # agar navigation-cancel patch already apply ho chuka hai:
            '''            onGifReceived = { uri, _, release ->
                UploadScope.io.launch {
                    // Backend sirf "image" | "document" category accept karta hai — "gif" bhejne se
                    // type silently "text" pe fallback ho jata tha aur chat me raw URL dikhta tha.
                    // "image" bhejo, MessageBubble already isko AsyncImage se render karta hai.
                    uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db, skipCompression = true, setUploading = {})
                    release()
                }
            },''',
            # agar abhi tak original state hai (scope.launch):
            '''            onGifReceived = { uri, _, release ->
                scope.launch {
                    // Backend sirf "image" | "document" category accept karta hai — "gif" bhejne se
                    // type silently "text" pe fallback ho jata tha aur chat me raw URL dikhta tha.
                    // "image" bhejo, MessageBubble already isko AsyncImage se render karta hai.
                    uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db, skipCompression = true, setUploading = {})
                    release()
                }
            },''',
        ],
        '''            onGifReceived = { uri, _, release ->
                UploadScope.io.launch {
                    // Backend "gif" category/type ko ab fully support karta hai — raw passthrough,
                    // koi re-compress nahi (routes/chat.js check kiya). Isliye "image" ki jagah
                    // "gif" bhejo: MessageBubble ka already-built borderless sticker-style bubble
                    // (no orange/green background, poora Fit render) turant unlock ho jaata hai.
                    uploadMediaMessage(context, uri, "gif", myToken, roomId, myUid, receiverUid, receiverUsername, db, skipCompression = true, setUploading = {})
                    release()
                }
            },''',
    ),
])


# ── 2) GroupChatScreen.kt — onGifReceived ───────────────────────────────────
group_chat_screen = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
ensure_import(group_chat_screen, "import com.muwan.muwanchat.data.UploadScope", "import com.muwan.muwanchat.data.UploadProgressTracker")
patch_file(group_chat_screen, [
    (
        [
            '''                onGifReceived = { uri, _, release ->
                    scope.launch {
                        uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db, skipCompression = true, setUploading = {})
                        release()
                    }
                },''',
        ],
        '''                onGifReceived = { uri, _, release ->
                    UploadScope.io.launch {
                        // "image" ki jagah "gif" — backend gif category/type ko as-is store karta
                        // hai (koi re-compress nahi), isse sticker-style borderless bubble aur
                        // "🎬 GIF" list preview dono unlock ho jaate hain (1:1 chat jaisa hi). Scope
                        // bhi UploadScope pe move kiya (navigate-away cancel se bachne ke liye).
                        uploadGroupMediaMessage(context, uri, "gif", myToken, groupId, myUid, groupId, groupName, db, skipCompression = true, setUploading = {})
                        release()
                    }
                },''',
    ),
])


# ── 3) ChatRepository.kt — conversation-list preview label ──────────────────
chat_repo = "app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt"
patch_file(chat_repo, [
    (
        [
            '''        val previewText = if (type == "text") content else when (type) {
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"''',
        ],
        '''        val previewText = if (type == "text") content else when (type) {
            "image" -> "📷 Photo"
            "gif" -> "🎬 GIF"
            "video" -> "🎥 Video"''',
    ),
    (
        [
            '''        val previewText = when (latest.type) {
            "text" -> latest.content
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"''',
        ],
        '''        val previewText = when (latest.type) {
            "text" -> latest.content
            "image" -> "📷 Photo"
            "gif" -> "🎬 GIF"
            "video" -> "🎥 Video"''',
    ),
])

print()
print("Done. Ab GIF/sticker (Gboard se ho ya kisi bhi GIF keyboard se) 'gif' type")
print("ke saath jaayega — MessageBubble ka already-built borderless sticker bubble")
print("use hoga (bina orange/green background ke, poora Fit render, koi crop nahi),")
print("aur conversation list mein '🎬 GIF' dikhega (raw URL text nahi).")
