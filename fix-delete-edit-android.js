const fs = require('fs');

// ── ChatApi.kt: add room_id query param ──
let api = 'app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt';
let a = fs.readFileSync(api, 'utf8');
const oldApi = `    @PUT("chat/message/{id}")
    suspend fun editMessage(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body request: EditMessageRequest
    ): Response<SendMessageResponse>

    @DELETE("chat/message/{id}")
    suspend fun deleteMsgById(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Map<String, Boolean>>`;
const newApi = `    @PUT("chat/message/{id}")
    suspend fun editMessage(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Query("room_id") roomId: String,
        @Body request: EditMessageRequest
    ): Response<SendMessageResponse>

    @DELETE("chat/message/{id}")
    suspend fun deleteMsgById(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Query("room_id") roomId: String
    ): Response<Map<String, Boolean>>`;
if (!a.includes(oldApi)) { console.log('ChatApi.kt pattern not found!'); process.exit(1); }
fs.writeFileSync(api, a.replace(oldApi, newApi));
console.log('ChatApi.kt patched');

// ── ChatScreen.kt: pass roomId in the calls ──
let scr = 'app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt';
let s = fs.readFileSync(scr, 'utf8');
const oldDel = 'RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", id)';
const newDel = 'RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", id, roomId)';
const oldEdit = 'RetrofitClient.chatApi.editMessage("Bearer $myToken", msg.id, EditMessageRequest(newText))';
const newEdit = 'RetrofitClient.chatApi.editMessage("Bearer $myToken", msg.id, roomId, EditMessageRequest(newText))';
if (!s.includes(oldDel)) { console.log('delete call pattern not found!'); process.exit(1); }
if (!s.includes(oldEdit)) { console.log('edit call pattern not found!'); process.exit(1); }
s = s.replace(oldDel, newDel).replace(oldEdit, newEdit);
fs.writeFileSync(scr, s);
console.log('ChatScreen.kt patched');
