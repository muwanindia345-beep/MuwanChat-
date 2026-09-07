# -*- coding: utf-8 -*-
# ChatApi.kt me broadcast-channel creation ke liye client-side pieces:
#   1. GroupData me isChannel field (default false -- backward compatible,
#      backend ke getGroup() default-merge se hi match karta hai)
#   2. CreateChannelRequest (name/avatar/description, memberUids nahi)
#      aur CreateChannelResponse
#   3. POST groups/create-channel endpoint (backend already deployed hai)

path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_groupdata = '''    val readReceiptsEnabled: Boolean = true,
    val pendingRequests: List<JoinRequestEntry> = emptyList()
)'''

new_groupdata = '''    val readReceiptsEnabled: Boolean = true,
    val pendingRequests: List<JoinRequestEntry> = emptyList(),
    val isChannel: Boolean = false
)'''

n = src.count(old_groupdata)
if n != 1:
    raise SystemExit(f"[FAIL] GroupData class: found {n} matches (expected 1)")
src = src.replace(old_groupdata, new_groupdata, 1)

old_reqs = '''data class CreateGroupResponse(
    val success: Boolean,
    val group: GroupData?
)'''

new_reqs = '''data class CreateGroupResponse(
    val success: Boolean,
    val group: GroupData?
)

data class CreateChannelRequest(
    val name: String,
    val avatar: String?,
    val description: String? = null
)

data class CreateChannelResponse(
    val success: Boolean,
    val group: GroupData?
)'''

n2 = src.count(old_reqs)
if n2 != 1:
    raise SystemExit(f"[FAIL] CreateGroupResponse block: found {n2} matches (expected 1)")
src = src.replace(old_reqs, new_reqs, 1)

old_endpoint = '''interface ChatApi {
    @POST("groups/create")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body request: CreateGroupRequest
    ): Response<CreateGroupResponse>'''

new_endpoint = '''interface ChatApi {
    @POST("groups/create")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body request: CreateGroupRequest
    ): Response<CreateGroupResponse>

    @POST("groups/create-channel")
    suspend fun createChannel(
        @Header("Authorization") token: String,
        @Body request: CreateChannelRequest
    ): Response<CreateChannelResponse>'''

n3 = src.count(old_endpoint)
if n3 != 1:
    raise SystemExit(f"[FAIL] ChatApi interface anchor: found {n3} matches (expected 1)")
src = src.replace(old_endpoint, new_endpoint, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] GroupData.isChannel + CreateChannelRequest/Response + createChannel() endpoint added")
