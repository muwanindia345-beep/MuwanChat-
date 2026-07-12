import io
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old = """import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check"""

new = """import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert"""

count = content.count(old)
if count != 1:
    print(f"MATCH FAILED: found {count}, expected 1")
else:
    content = content.replace(old, new)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Patched GroupChatScreen.kt imports")
