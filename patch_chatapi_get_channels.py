# -*- coding: utf-8 -*-
# ChatApi.kt me getChannels() endpoint add karta hai (naye backend route
# GET /chat/channels/mine ko hit karta hai) -- response shape existing
# getConversations() jaisa hi hai, isliye ConversationsResponse hi reuse
# hota hai, koi naya model nahi chahiye.

path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old = '''    @GET("chat/conversations")
    suspend fun getConversations(
        @Header("Authorization") token: String
    ): Response<ConversationsResponse>'''

new = '''    @GET("chat/conversations")
    suspend fun getConversations(
        @Header("Authorization") token: String
    ): Response<ConversationsResponse>

    @GET("chat/channels/mine")
    suspend fun getChannels(
        @Header("Authorization") token: String
    ): Response<ConversationsResponse>'''

n = src.count(old)
if n != 1:
    raise SystemExit(f"[FAIL] getConversations anchor: found {n} matches (expected 1)")
src = src.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] getChannels() endpoint added to ChatApi")
