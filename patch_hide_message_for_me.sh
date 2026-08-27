#!/data/data/com.termux/files/usr/bin/bash
# patch_hide_message_for_me.sh
# Wires up server-side "delete for me" persistence on the Android side.
# Adds a new Retrofit endpoint (hideMessageForMe) and calls it from
# deleteSelectedForMe() in both 1-on-1 and group chat, in addition to
# the existing local delete — so the message stays hidden for you even
# after an app reinstall or local DB reset.
# Requires: patch_backend_hide_message.sh must already be deployed on
# the backend, otherwise this call will just fail silently (best-effort,
# won't break anything, but won't persist either).
# Run from project root (MuwanChat--main folder):
#   bash patch_hide_message_for_me.sh

set -e

API_FILE="app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
CHAT_FILE="app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
GROUP_FILE="app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

for f in "$API_FILE" "$CHAT_FILE" "$GROUP_FILE"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: $f not found. Run this script from the MuwanChat--main root folder."
        exit 1
    fi
done

python3 - << 'PYEOF'
def patch(path, replacements, label):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    changed = False
    for old, new in replacements:
        if new in content:
            print(f"SKIP ({label}): already patched")
            continue
        if old in content:
            content = content.replace(old, new, 1)
            changed = True
        else:
            print(f"WARN ({label}): anchor not found — already patched or changed manually?")
    if changed:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Patched: {path}")

# ───────────────────────── ChatApi.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt",
    [
        (
            '''    @DELETE("chat/message/{roomId}/{id}")
    suspend fun deleteMsgById(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("id") id: String
    ): Response<Map<String, Boolean>>

    @POST("chat/message/{roomId}/{id}/react")''',
            '''    @DELETE("chat/message/{roomId}/{id}")
    suspend fun deleteMsgById(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("id") id: String
    ): Response<Map<String, Boolean>>

    // "Delete for me" (single message) ko server pe bhi persist karta hai,
    // taaki app reinstall / local DB reset ke baad bhi message wapas na aaye.
    // Dusre user ko is se koi farak nahi padta, sirf khud ke liye hide hota hai.
    @POST("chat/message/{roomId}/{id}/hide")
    suspend fun hideMessageForMe(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("id") id: String
    ): Response<Map<String, Boolean>>

    @POST("chat/message/{roomId}/{id}/react")'''
        ),
    ],
    "ChatApi.kt"
)

# ───────────────────────── ChatScreen.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt",
    [
        (
            '''    fun deleteSelectedForMe() {
        val ids = selectedMessageIds.toList()
        val now = nowIso()
        scope.launch {
            db.messageDao().deleteByIds(ids)
            // Record karo taaki agla sync backend se inhe wapas na le aaye
            db.deletedMessageDao().markDeleted(ids.map { DeletedMessageEntity(it, now) })
            ChatRepository.refreshLastMessagePreview(db, roomId)
        }
        exitSelectionMode()
    }''',
            '''    fun deleteSelectedForMe() {
        val ids = selectedMessageIds.toList()
        val now = nowIso()
        scope.launch {
            db.messageDao().deleteByIds(ids)
            // Record karo taaki agla sync backend se inhe wapas na le aaye
            db.deletedMessageDao().markDeleted(ids.map { DeletedMessageEntity(it, now) })
            // Server pe bhi persist karo -- taaki app reinstall/DB reset ke
            // baad bhi yeh message wapas na aaye (best-effort: local delete
            // turant ho chuka hai, backend call fail ho bhi jaaye to UI par
            // koi farak nahi padta, bas reinstall-persistence miss hogi)
            ids.forEach { id ->
                try {
                    RetrofitClient.chatApi.hideMessageForMe("Bearer $myToken", roomId, id)
                } catch (_: Exception) {}
            }
            ChatRepository.refreshLastMessagePreview(db, roomId)
        }
        exitSelectionMode()
    }'''
        ),
    ],
    "ChatScreen.kt"
)

# ───────────────────────── GroupChatScreen.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt",
    [
        (
            '''    fun deleteSelectedForMe() {
        val ids = selectedMessageIds.toList()
        val now = nowIso()
        scope.launch {
            db.messageDao().deleteByIds(ids)
            db.deletedMessageDao().markDeleted(ids.map { DeletedMessageEntity(it, now) })
            ChatRepository.refreshLastMessagePreview(db, groupId)
        }
        exitSelectionMode()
    }''',
            '''    fun deleteSelectedForMe() {
        val ids = selectedMessageIds.toList()
        val now = nowIso()
        scope.launch {
            db.messageDao().deleteByIds(ids)
            db.deletedMessageDao().markDeleted(ids.map { DeletedMessageEntity(it, now) })
            ids.forEach { id ->
                try {
                    RetrofitClient.chatApi.hideMessageForMe("Bearer $myToken", groupId, id)
                } catch (_: Exception) {}
            }
            ChatRepository.refreshLastMessagePreview(db, groupId)
        }
        exitSelectionMode()
    }'''
        ),
    ],
    "GroupChatScreen.kt"
)
PYEOF

echo ""
echo "Verifying brace/paren balance..."
for f in "$API_FILE" "$CHAT_FILE" "$GROUP_FILE"; do
    python3 -c "
content = open('$f').read()
o, c = content.count('{'), content.count('}')
po, pc = content.count('('), content.count(')')
status = 'OK' if (o == c and po == pc) else 'MISMATCH!'
print(f'$f -> braces {o}/{c}, parens {po}/{pc} -> {status}')
"
done
