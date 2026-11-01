import sys, os

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat/screens")
MB = os.path.join(BASE, "MessageBubble.kt")
RING = os.path.join(BASE, "UploadProgressRing.kt")

s = open(MB, encoding="utf-8").read()
if "UPLOAD_RING_FIX" not in s or not os.path.exists(RING):
    print("PEHLE upload_ring_patch.py chalao (photo/video wala), phir ye.")
    sys.exit(1)
if "DOC_RING_FIX" in s:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: matched %d times" % (name, n))
        sys.exit(1)
    return s.replace(old, new)

s = rep(s,
'''                            Icon(Icons.Filled.Description, contentDescription = "Document", tint = Color.White)''',
'''                            // DOC_RING_FIX: upload ke dauran file icon ki jagah ring + %
                            if (message.status == "UPLOADING") {
                                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    UploadProgressRingSmall(UploadProgressTracker.progress[message.id])
                                }
                            } else {
                                Icon(Icons.Filled.Description, contentDescription = "Document", tint = Color.White)
                            }''', "document icon")

s = rep(s,
'''!((message.type == "image" || message.type == "video") && message.mediaUrl != null)''',
'''!((message.type == "image" || message.type == "video" || message.type == "document") && message.mediaUrl != null)''',
"bottom bar condition")
open(MB, "w", encoding="utf-8").write(s)
print("OK: MessageBubble.kt")

r = open(RING, encoding="utf-8").read()
r = rep(r,
"import androidx.compose.foundation.layout.size\n",
"import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.wrapContentSize\n",
"ring import")
r += '''
// DOC_RING_FIX: document row ke liye chhoti ring. Parent slot 24dp hi rehta hai
// (wrapContentSize unbounded) taaki bubble ki height/width upload ke dauran jump na kare.
@Composable
fun UploadProgressRingSmall(percent: Int?, modifier: Modifier = Modifier) {
    val pct = (percent ?: 0).coerceIn(0, 100)
    val animated by animateFloatAsState(
        targetValue = pct / 100f,
        animationSpec = tween(durationMillis = 150),
        label = "uploadRingSmall"
    )
    Box(
        modifier = modifier
            .wrapContentSize(unbounded = true)
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxSize().padding(2.dp),
            color = Color(0xFFFF7A1A),
            strokeWidth = 3.dp,
            trackColor = Color(0x40FFFFFF)
        )
        Text(
            "$pct%",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
'''
open(RING, "w", encoding="utf-8").write(r)
print("OK: UploadProgressRing.kt")
print("DONE")
