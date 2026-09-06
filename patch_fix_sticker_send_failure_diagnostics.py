# -*- coding: utf-8 -*-
# "Sticker sent nahi ho raha, GIF ho gaya" issue: uploadMediaMessage() ka
# catch block exception ko silently nigal leta tha (`catch (_: Exception)`),
# aur HTTP failure branch bhi koi reason store/log nahi karta tha -- sirf
# FAILED maar deta tha. Isliye ab tak pata nahi chal sakta tha ki fail kyun
# ho raha hai (payload size? timeout? server 413/500?).
#
# Yeh patch teen cheezein karta hai:
#   1. Har FAILED path pe asli exception/HTTP code Logcat me likhta hai
#      (tag "MuwanUpload") -- agli baar fail ho to `adb logcat` / Android
#      Studio logcat me exact wajah dikhegi.
#   2. User ko bhi turant pata chale isliye ek chhota Toast dikhata hai
#      jab gif/sticker/image FAILED ho ("Sticker/GIF nahi bheja gaya —
#      wajah: <reason>").
#   3. Naya suspected root cause note: category="gif" hamesha
#      skipCompression=true rehta hai (raw passthrough, animation preserve
#      karne ke liye), isliye keyboard se aane wale bade sticker files
#      (kuch MB tak) bina compress kiye base64 me jaate hain -- agar backend
#      ya network payload limit se bade ho gaye to woh silently FAILED ho
#      rahe honge jabki chhote/optimized GIFs (jaise Tenor se) pass ho jaate
#      hain. Logs se ab yeh confirm ho sakega.

path = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old = '''        val res = RetrofitClient.chatApi.uploadMedia(
            "Bearer $token",
            UploadMediaRequest(filename = filename, mime_type = mime, data = base64Data, category = category),
            uploadId = id
        )
        if (res.isSuccessful) {
            res.body()?.let { body ->
                db.messageDao().updateMediaContent(id, body.url, "PENDING")
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendMessage(id, receiverUid, body.url, displayType, body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    try {
                        val sendRes = RetrofitClient.chatApi.sendMessage(
                            "Bearer $token",
                            SendMessageRequest(receiverUid, body.url, displayType, body.file_name ?: filename, body.mime_type ?: mime)
                        )
                        db.messageDao().updateStatus(id, if (sendRes.isSuccessful) "SENT" else "FAILED")
                    } catch (_: Exception) {
                        db.messageDao().updateStatus(id, "FAILED")
                    }
                }
            } ?: db.messageDao().updateStatus(id, "FAILED")
        } else {
            db.messageDao().updateStatus(id, "FAILED")
        }
    } catch (_: Exception) {
        db.messageDao().updateStatus(id, "FAILED")
    } finally {
        UploadProgressTracker.clear(id)
        setUploading(false)
    }
}
// ─── Upload + send: video (multipart → Cloudinary) ─────────────────────────'''

new = '''        val payloadKb = base64Data.length / 1024
        android.util.Log.d("MuwanUpload", "uploadMedia start id=$id category=$category sizeKb=$payloadKb")
        val res = RetrofitClient.chatApi.uploadMedia(
            "Bearer $token",
            UploadMediaRequest(filename = filename, mime_type = mime, data = base64Data, category = category),
            uploadId = id
        )
        if (res.isSuccessful) {
            res.body()?.let { body ->
                db.messageDao().updateMediaContent(id, body.url, "PENDING")
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendMessage(id, receiverUid, body.url, displayType, body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                            if (!success) {
                                android.util.Log.e("MuwanUpload", "send_message ack failed id=$id category=$category sizeKb=$payloadKb")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "$displayType nahi bheja gaya (ack failed, $payloadKb KB)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } else {
                    try {
                        val sendRes = RetrofitClient.chatApi.sendMessage(
                            "Bearer $token",
                            SendMessageRequest(receiverUid, body.url, displayType, body.file_name ?: filename, body.mime_type ?: mime)
                        )
                        db.messageDao().updateStatus(id, if (sendRes.isSuccessful) "SENT" else "FAILED")
                        if (!sendRes.isSuccessful) {
                            android.util.Log.e("MuwanUpload", "sendMessage http failed id=$id code=${sendRes.code()} category=$category sizeKb=$payloadKb")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MuwanUpload", "sendMessage exception id=$id category=$category sizeKb=$payloadKb", e)
                        db.messageDao().updateStatus(id, "FAILED")
                    }
                }
            } ?: run {
                android.util.Log.e("MuwanUpload", "uploadMedia body null id=$id category=$category sizeKb=$payloadKb")
                db.messageDao().updateStatus(id, "FAILED")
            }
        } else {
            val errBody = try { res.errorBody()?.string() } catch (_: Exception) { null }
            android.util.Log.e("MuwanUpload", "uploadMedia http failed id=$id code=${res.code()} category=$category sizeKb=$payloadKb body=$errBody")
            db.messageDao().updateStatus(id, "FAILED")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "$displayType upload fail (HTTP ${res.code()}, $payloadKb KB)", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("MuwanUpload", "uploadMediaMessage exception id=$id category=$category", e)
        db.messageDao().updateStatus(id, "FAILED")
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "$displayType nahi bheja gaya: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    } finally {
        UploadProgressTracker.clear(id)
        setUploading(false)
    }
}
// ─── Upload + send: video (multipart → Cloudinary) ─────────────────────────'''

n = src.count(old)
if n != 1:
    raise SystemExit(f"[FAIL] sticker send-failure diagnostics: found {n} matches (expected 1)")
src = src.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Added Logcat + Toast diagnostics for gif/image upload failures")
