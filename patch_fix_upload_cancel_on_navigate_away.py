# Fix: video/document/music (aur camera-video/photo) upload beech mein hi cancel
# ho jaata tha jab user chat screen se bahar navigate kar deta tha (ya app
# background kar deta tha) — bubble hamesha ke liye "UPLOADING" / 0% par stuck
# reh jaata tha, na load hota tha na sahi se send hota tha.
#
# ROOT CAUSE:
#   ChatScreen.kt aur GroupChatScreen.kt dono mein "val scope = rememberCoroutineScope()"
#   Compose composition se bandha hota hai — jaise hi screen dispose hoti hai
#   (navigate away / back press), yeh scope turant cancel ho jaata hai. Saare
#   upload*Message() calls isi scope.launch{} ke andar chal rahe the, matlab
#   upload ka poora network call (aur uske baad DB status ko SENT/FAILED update
#   karna) beech mein hi mar jaata tha. UploadProgressTracker (jo already ek
#   global singleton hai, theek hai) ka entry clear ho jaata lekin DB row
#   "UPLOADING" par hi reh jaata — isliye wapas screen par aane par pct null +
#   status UPLOADING = stuck 0% bar.
#
#   Photo kam dikhta tha kyunki woh chhota/tez hota hai (upload user ke navigate
#   karne se pehle hi usually poora ho jaata), lekin same root cause usko bhi
#   affect karta hai — is fix ke baad photo bhi consistent hai.
#
# FIX:
#   Naya app-lifetime CoroutineScope (UploadScope) banaya jo Compose se bilkul
#   independent hai — screen dispose hone se cancel nahi hota. Sirf upload
#   calls (image/video/document/music/camera/voice) ab isi scope se launch
#   honge; baaki sab (typing indicator, edit message, scroll, etc.) waise hi
#   rememberCoroutineScope() par rehte hain, unko change karne ki zaroorat nahi.
#
# Termux mein repo root (jahan app/ folder hai) se run karo:
#   python patch_fix_upload_cancel_on_navigate_away.py

import os

# ── 1) Naya UploadScope.kt file ─────────────────────────────────────────────
upload_scope_path = "app/src/main/java/com/muwan/muwanchat/data/UploadScope.kt"
upload_scope_content = '''package com.muwan.muwanchat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Media uploads (photo/video/document/music/voice) ke liye app-lifetime scope.
// rememberCoroutineScope() (Compose) ke bajaye isko use karo taaki user chat
// screen se navigate away ho jaaye (ya app background chala jaaye) to bhi
// upload background mein chalta rahe aur poora complete ho — status update
// (SENT/FAILED) bhi ho jaaye. Process ke jeete rehte tak yeh zinda rehta hai.
object UploadScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
'''

os.makedirs(os.path.dirname(upload_scope_path), exist_ok=True)
if os.path.exists(upload_scope_path):
    print(f"SKIP (already exists): {upload_scope_path}")
else:
    with open(upload_scope_path, "w") as f:
        f.write(upload_scope_content)
    print(f"CREATED: {upload_scope_path}")


def patch_file(path, replacements, import_anchor):
    s = open(path).read()
    orig = s

    # import add karo (agar already nahi hai)
    if "import com.muwan.muwanchat.data.UploadScope" not in s:
        assert import_anchor in s, f"import anchor not found in {path}"
        s = s.replace(import_anchor, import_anchor + "\nimport com.muwan.muwanchat.data.UploadScope", 1)

    for old, new in replacements:
        count = s.count(old)
        assert count == 1, f"expected 1 match, found {count} in {path} for:\n{old}"
        s = s.replace(old, new, 1)

    if s == orig:
        print(f"NO CHANGE: {path}")
    else:
        with open(path, "w") as f:
            f.write(s)
        print(f"PATCHED: {path}")


# ── 2) ChatScreen.kt ─────────────────────────────────────────────────────────
chat_screen = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
chat_replacements = [
    (
        '''    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db) {}
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadVideoMessage(context, it, myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { uploadMediaMessage(context, it, "document", myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadMediaMessage(context, it, "audio", myToken, roomId, myUid, receiverUid, receiverUsername, db, displayType = "music") {} } }
    }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri -> scope.launch { uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }
        }
    }''',
        '''    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            UploadScope.io.launch {
                uris.forEach { uri ->
                    uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db) {}
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { UploadScope.io.launch { uploadVideoMessage(context, it, myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { UploadScope.io.launch { uploadMediaMessage(context, it, "document", myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { UploadScope.io.launch { uploadMediaMessage(context, it, "audio", myToken, roomId, myUid, receiverUid, receiverUsername, db, displayType = "music") {} } }
    }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri -> UploadScope.io.launch { uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }
        }
    }''',
    ),
    (
        '''            cameraVideoUri?.let { uri -> scope.launch { uploadVideoMessage(context, uri, myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }''',
        '''            cameraVideoUri?.let { uri -> UploadScope.io.launch { uploadVideoMessage(context, uri, myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }''',
    ),
    (
        '''            onGifReceived = { uri, _, release ->
                scope.launch {
                    // Backend sirf "image" | "document" category accept karta hai — "gif" bhejne se
                    // type silently "text" pe fallback ho jata tha aur chat me raw URL dikhta tha.
                    // "image" bhejo, MessageBubble already isko AsyncImage se render karta hai.
                    uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db, skipCompression = true, setUploading = {})
                    release()
                }
            },''',
        '''            onGifReceived = { uri, _, release ->
                UploadScope.io.launch {
                    // Backend sirf "image" | "document" category accept karta hai — "gif" bhejne se
                    // type silently "text" pe fallback ho jata tha aur chat me raw URL dikhta tha.
                    // "image" bhejo, MessageBubble already isko AsyncImage se render karta hai.
                    uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db, skipCompression = true, setUploading = {})
                    release()
                }
            },''',
    ),
    (
        '''            onSend = { file ->
                showVoiceRecorder = false
                scope.launch {
                    uploadAudioMessage(context, file, myToken, roomId, myUid, receiverUid, receiverUsername, db) {}
                }
            }''',
        '''            onSend = { file ->
                showVoiceRecorder = false
                UploadScope.io.launch {
                    uploadAudioMessage(context, file, myToken, roomId, myUid, receiverUid, receiverUsername, db) {}
                }
            }''',
    ),
]
patch_file(chat_screen, chat_replacements, "import com.muwan.muwanchat.data.UploadProgressTracker")


# ── 3) GroupChatScreen.kt ────────────────────────────────────────────────────
group_chat_screen = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
group_replacements = [
    (
        '''    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db) {}
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadGroupVideoMessage(context, it, myToken, groupId, myUid, groupId, groupName, db) {} } }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { uploadGroupMediaMessage(context, it, "document", myToken, groupId, myUid, groupId, groupName, db) {} } }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadGroupMediaMessage(context, it, "audio", myToken, groupId, myUid, groupId, groupName, db, displayType = "music") {} } }
    }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri -> scope.launch { uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db) {} } }
        }
    }''',
        '''    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            UploadScope.io.launch {
                uris.forEach { uri ->
                    uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db) {}
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { UploadScope.io.launch { uploadGroupVideoMessage(context, it, myToken, groupId, myUid, groupId, groupName, db) {} } }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { UploadScope.io.launch { uploadGroupMediaMessage(context, it, "document", myToken, groupId, myUid, groupId, groupName, db) {} } }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { UploadScope.io.launch { uploadGroupMediaMessage(context, it, "audio", myToken, groupId, myUid, groupId, groupName, db, displayType = "music") {} } }
    }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri -> UploadScope.io.launch { uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db) {} } }
        }
    }''',
    ),
    (
        '''            cameraVideoUri?.let { uri -> scope.launch { uploadGroupVideoMessage(context, uri, myToken, groupId, myUid, groupId, groupName, db) {} } }''',
        '''            cameraVideoUri?.let { uri -> UploadScope.io.launch { uploadGroupVideoMessage(context, uri, myToken, groupId, myUid, groupId, groupName, db) {} } }''',
    ),
    (
        '''            onSend = { file ->
                showVoiceRecorder = false
                scope.launch {
                    uploadGroupAudioMessage(context, file, myToken, groupId, myUid, groupId, groupName, db) {}
                }
            }''',
        '''            onSend = { file ->
                showVoiceRecorder = false
                UploadScope.io.launch {
                    uploadGroupAudioMessage(context, file, myToken, groupId, myUid, groupId, groupName, db) {}
                }
            }''',
    ),
]
patch_file(group_chat_screen, group_replacements, "import com.muwan.muwanchat.data.UploadProgressTracker")

print()
print("Done. Ab video/document/music/photo/camera/voice — sab uploads screen se")
print("navigate away hone ke baad bhi background mein chalte rahenge aur complete")
print("hote hi status SENT/FAILED me update ho jaayega. Wapas chat screen kholne par")
print("MessageBubble turant sahi % ya final state dikhayega, stuck 0% wala bug nahi aayega.")
