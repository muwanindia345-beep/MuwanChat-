import sys, os

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat/screens")
MB = os.path.join(BASE, "MessageBubble.kt")
AP = os.path.join(BASE, "AudioMessagePlayer.kt")

mb = open(MB, encoding="utf-8").read()
ap = open(AP, encoding="utf-8").read()
if "DOC_RING_FIX" not in mb:
    print("PEHLE upload_ring_patch.py aur doc_ring_patch.py chalao, phir ye.")
    sys.exit(1)
if "AUDIO_RING_FIX" in ap:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: matched %d times" % (name, n))
        sys.exit(1)
    return s.replace(old, new)

ap = rep(ap,
"import androidx.compose.material3.Icon\n",
"import androidx.compose.animation.core.animateFloatAsState\nimport androidx.compose.animation.core.tween\nimport androidx.compose.material3.CircularProgressIndicator\nimport androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.material3.Icon\n",
"imports")

ap = rep(ap,
"fun AudioMessagePlayer(url: String, sent: Boolean) {",
"fun AudioMessagePlayer(\n    url: String,\n    sent: Boolean,\n    uploading: Boolean = false, // AUDIO_RING_FIX\n    uploadPercent: Int? = null\n) {",
"signature")

ap = rep(ap,
"                .clickable(enabled = isPrepared) {",
"                .clickable(enabled = isPrepared && !uploading) {",
"clickable")

ap = rep(ap,
'''            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )''',
'''            // AUDIO_RING_FIX: upload ke dauran play icon ki jagah ring + %
            if (uploading) {
                val pct = (uploadPercent ?: 0).coerceIn(0, 100)
                val animatedPct by animateFloatAsState(
                    targetValue = pct / 100f,
                    animationSpec = tween(durationMillis = 150),
                    label = "audioUploadRing"
                )
                CircularProgressIndicator(
                    progress = { animatedPct },
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    color = Color(0xFFFF7A1A),
                    strokeWidth = 3.dp,
                    trackColor = Color(0x40FFFFFF)
                )
                Text(
                    "$pct%",
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }''', "play icon")
open(AP, "w", encoding="utf-8").write(ap)
print("OK: AudioMessagePlayer.kt")

mb = rep(mb,
"AudioMessagePlayer(url = url, sent = message.sent)",
"AudioMessagePlayer(\n                            url = url,\n                            sent = message.sent,\n                            uploading = message.status == \"UPLOADING\",\n                            uploadPercent = UploadProgressTracker.progress[message.id]\n                        )",
"call site")
mb = rep(mb,
'''!((message.type == "image" || message.type == "video" || message.type == "document") && message.mediaUrl != null)''',
'''!((message.type == "image" || message.type == "video" || message.type == "document" ||
                        message.type == "audio" || message.type == "music") && message.mediaUrl != null)''',
"bottom bar condition")
open(MB, "w", encoding="utf-8").write(mb)
print("OK: MessageBubble.kt")
print("DONE")
