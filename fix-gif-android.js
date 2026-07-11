const fs = require('fs');

let p1 = 'app/src/main/java/com/muwan/muwanchat/screens/ChatInputBar.kt';
let c1 = fs.readFileSync(p1, 'utf8');

const oldImports = `import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent`;
const newImports = `import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.addTextChangedListener
import android.net.Uri
import com.muwan.muwanchat.DarkAccent`;
if (!c1.includes(oldImports)) { console.log('ChatInputBar imports pattern not found!'); process.exit(1); }
c1 = c1.replace(oldImports, newImports);

const oldSig = `    onSend: () -> Unit,
    onVoiceMessage: () -> Unit
) {`;
const newSig = `    onSend: () -> Unit,
    onVoiceMessage: () -> Unit,
    onGifReceived: (Uri, String, () -> Unit) -> Unit = { _, _, release -> release() }
) {`;
if (!c1.includes(oldSig)) { console.log('ChatInputBar signature pattern not found!'); process.exit(1); }
c1 = c1.replace(oldSig, newSig);

const oldField = `            TextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "Message...",
                        color = Color(0xFF888888),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = false,
                maxLines = 4
            )`;
const newField = `            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                factory = { ctx ->
                    GifAwareEditText(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTextColor(android.graphics.Color.WHITE)
                        setHintTextColor(android.graphics.Color.parseColor("#888888"))
                        setHighlightColor(android.graphics.Color.parseColor("#55FF7043"))
                        hint = "Message..."
                        textSize = 14f
                        setPadding(0, 0, 0, 0)
                        isSingleLine = false
                        maxLines = 4
                        addTextChangedListener { editable ->
                            val text = editable?.toString() ?: ""
                            if (text != input) onInputChange(text)
                        }
                        onContentReceived = { uri, mime, release ->
                            onGifReceived(uri, mime, release)
                        }
                    }
                },
                update = { view ->
                    if (view.text.toString() != input) {
                        view.setText(input)
                        view.setSelection(input.length.coerceIn(0, view.text.length))
                    }
                }
            )`;
if (!c1.includes(oldField)) { console.log('ChatInputBar TextField pattern not found!'); process.exit(1); }
c1 = c1.replace(oldField, newField);
fs.writeFileSync(p1, c1);
console.log('ChatInputBar.kt patched');

let p2 = 'app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt';
let c2 = fs.readFileSync(p2, 'utf8');
const oldWire = `            onPickImage = { showMediaSheet = true },
            onSend = { sendMessage() },
            onVoiceMessage = {`;
const newWire = `            onPickImage = { showMediaSheet = true },
            onSend = { sendMessage() },
            onGifReceived = { uri, _, release ->
                scope.launch {
                    uploadMediaMessage(context, uri, "gif", myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it }
                    release()
                }
            },
            onVoiceMessage = {`;
if (!c2.includes(oldWire)) { console.log('ChatScreen wiring pattern not found!'); process.exit(1); }
c2 = c2.replace(oldWire, newWire);
fs.writeFileSync(p2, c2);
console.log('ChatScreen.kt patched');

let p3 = 'app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt';
let c3 = fs.readFileSync(p3, 'utf8');
const oldMedia = `    val isMedia = message.type == "image" || message.type == "video" || message.type == "audio"`;
const newMedia = `    val isMedia = message.type == "image" || message.type == "gif" || message.type == "video" || message.type == "audio"`;
if (!c3.includes(oldMedia)) { console.log('isMedia pattern not found!'); process.exit(1); }
c3 = c3.replace(oldMedia, newMedia);
const oldSwitch = `                    "image" -> message.mediaUrl?.let { url ->`;
const newSwitch = `                    "image", "gif" -> message.mediaUrl?.let { url ->`;
if (!c3.includes(oldSwitch)) { console.log('MessageBubble switch pattern not found!'); process.exit(1); }
c3 = c3.replace(oldSwitch, newSwitch);
fs.writeFileSync(p3, c3);
console.log('MessageBubble.kt patched');

let p4 = 'app/src/main/java/com/muwan/muwanchat/MuwanChatApp.kt';
let c4 = fs.readFileSync(p4, 'utf8');
const oldApp = `import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

// Video message bubbles ke liye Coil ko sikhana padta hai video se ek frame nikalna —
// warna wo sirf default fallback (blank/broken) dikhata hai jaisa image ke liye karta hai.
class MuwanChatApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}`;
const newApp = `import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder

class MuwanChatApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}`;
if (!c4.includes(oldApp)) { console.log('MuwanChatApp pattern not found!'); process.exit(1); }
c4 = c4.replace(oldApp, newApp);
fs.writeFileSync(p4, c4);
console.log('MuwanChatApp.kt patched');

let p5 = 'app/build.gradle.kts';
let c5 = fs.readFileSync(p5, 'utf8');
const oldDep = `    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")`;
const newDep = `    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")`;
if (!c5.includes(oldDep)) { console.log('build.gradle.kts pattern not found!'); process.exit(1); }
c5 = c5.replace(oldDep, newDep);
fs.writeFileSync(p5, c5);
console.log('build.gradle.kts patched');
