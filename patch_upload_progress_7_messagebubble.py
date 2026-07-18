import io
path = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

# 1. imports
old = """import androidx.compose.material3.Icon
import androidx.compose.material3.Text"""
new = """import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import com.muwan.muwanchat.data.UploadProgressTracker"""
assert content.count(old) == 1, "match failed: imports (Icon/Text)"
content = content.replace(old, new)

old = """import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration"""
new = """import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration"""
assert content.count(old) == 1, "match failed: imports (FontWeight)"
content = content.replace(old, new)

# 2. gif/sticker inline time row: add UPLOADING -> clock mapping
old = """                                    val (icon, tint) = when (message.status) {
                                        "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                                        "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)"""
new = """                                    val (icon, tint) = when (message.status) {
                                        "UPLOADING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                                        "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                                        "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)"""
assert content.count(old) == 1, "match failed: gif status icon mapping"
content = content.replace(old, new)

# 3. main trailing time/status row: add UPLOADING -> clock mapping + the orange progress layer
old = """                    if (message.sent) {
                        Spacer(Modifier.width(4.dp))
                        val (icon, tint) = when (message.status) {
                            "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                            "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)
                            "FAILED" -> Icons.Filled.ErrorOutline to Color(0xFFE53935)
                            else -> Icons.Filled.Check to Color(0xAAFFFFFF)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = message.status,
                            tint = tint,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                }
            }
            }"""
new = """                    if (message.sent) {
                        Spacer(Modifier.width(4.dp))
                        val (icon, tint) = when (message.status) {
                            "UPLOADING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                            "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                            "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)
                            "FAILED" -> Icons.Filled.ErrorOutline to Color(0xFFE53935)
                            else -> Icons.Filled.Check to Color(0xAAFFFFFF)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = message.status,
                            tint = tint,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Real-time upload % layer — jab tak upload chal raha hai tabhi dikhta hai,
                // complete hote hi (status UPLOADING se hat jaate hi) automatically gayab
                if (message.status == "UPLOADING") {
                    val pct = UploadProgressTracker.progress[message.id]
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF7A1A))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { (pct ?: 0) / 100f },
                            modifier = Modifier
                                .width(70.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color(0x55FFFFFF)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${pct ?: 0}%",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                }
            }
            }"""
assert content.count(old) == 1, "match failed: main status row + progress layer"
content = content.replace(old, new)

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched MessageBubble.kt")
