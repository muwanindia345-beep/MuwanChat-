files_patches = {
    "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt": [
        ('''data class ReactResponse(
    val success: Boolean,
    val reactions: List<MessageReaction>? = null
)

interface ChatApi {''',
'''data class ReactResponse(
    val success: Boolean,
    val reactions: List<MessageReaction>? = null
)

data class WallpaperRequest(
    val type: String,
    val value: String
)

data class WallpaperData(
    val type: String,
    val value: String
)

data class WallpaperResponse(
    val wallpaper: WallpaperData?
)

interface ChatApi {''', "wallpaper models"),
        ('''    @GET("chat/link-preview")
    suspend fun linkPreview(
        @Header("Authorization") token: String,
        @Query("url") url: String
    ): Response<LinkPreviewResponse>
}''',
'''    @GET("chat/link-preview")
    suspend fun linkPreview(
        @Header("Authorization") token: String,
        @Query("url") url: String
    ): Response<LinkPreviewResponse>

    @POST("chat/wallpaper/{roomId}")
    suspend fun setWallpaper(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Body request: WallpaperRequest
    ): Response<Map<String, Boolean>>

    @GET("chat/wallpaper/{roomId}")
    suspend fun getWallpaper(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<WallpaperResponse>

    @DELETE("chat/wallpaper/{roomId}")
    suspend fun deleteWallpaper(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<Map<String, Boolean>>
}''', "wallpaper endpoints"),
    ],
    "app/src/main/java/com/muwan/muwanchat/screens/WallpaperScreen.kt": [
        ('''import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatWallpaperEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.WallpaperPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream''',
'''import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatWallpaperEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.WallpaperPresets
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.WallpaperRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream''', "imports"),
        ('''    fun applyPreset(type: String, id: String) {
        scope.launch { db.chatWallpaperDao().upsert(ChatWallpaperEntity(roomId, type, id)) }
    }

    fun removeWallpaper() {
        scope.launch { db.chatWallpaperDao().deleteByRoomId(roomId) }
    }''',
'''    fun applyPreset(type: String, id: String) {
        scope.launch {
            db.chatWallpaperDao().upsert(ChatWallpaperEntity(roomId, type, id))
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                RetrofitClient.chatApi.setWallpaper("Bearer $token", roomId, WallpaperRequest(type, id))
            } catch (_: Exception) {
                // Offline ho toh koi baat nahi, local wallpaper turant apply ho chuka hai
            }
        }
    }

    fun removeWallpaper() {
        scope.launch {
            db.chatWallpaperDao().deleteByRoomId(roomId)
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                RetrofitClient.chatApi.deleteWallpaper("Bearer $token", roomId)
            } catch (_: Exception) {
            }
        }
    }''', "sync on set/remove"),
    ],
    "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt": [
        ('''import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AudioRecorder
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.DeletedMessageEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.SocketEvent''',
'''import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AudioRecorder
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.ChatWallpaperEntity
import com.muwan.muwanchat.data.DeletedMessageEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.SocketEvent''', "import"),
        ('''    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        myToken = token
        myUid = AuthDataStore.getUid(context).first() ?: ""

        AppSocketManager.connect(token)
        AppSocketManager.joinRoom(roomId)
        AppSocketManager.checkPresence(receiverUid)

        try {''',
'''    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        myToken = token
        myUid = AuthDataStore.getUid(context).first() ?: ""

        AppSocketManager.connect(token)
        AppSocketManager.joinRoom(roomId)
        AppSocketManager.checkPresence(receiverUid)

        // Wallpaper local na mile (reinstall ke baad) toh backend se apna preset restore karo
        try {
            if (db.chatWallpaperDao().getByRoomId(roomId) == null) {
                val res = RetrofitClient.chatApi.getWallpaper("Bearer $token", roomId)
                res.body()?.wallpaper?.let { wp ->
                    db.chatWallpaperDao().upsert(ChatWallpaperEntity(roomId, wp.type, wp.value))
                }
            }
        } catch (_: Exception) {}

        try {''', "wallpaper restore on chat open"),
    ],
}

for path, patch_list in files_patches.items():
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    orig = content
    for old, new, label in patch_list:
        if old in content:
            content = content.replace(old, new)
            print(f"[OK] {path} — {label}")
        else:
            print(f"[SKIP] {path} — {label} (not found)")
    if content != orig:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
