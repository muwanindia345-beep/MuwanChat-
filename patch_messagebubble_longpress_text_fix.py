import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_import = "import androidx.compose.runtime.remember"
new_import = "import androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember"
if old_import not in content:
    print("Import anchor not found!")
    sys.exit(1)
content = content.replace(old_import, new_import, 1)

old_text_import = "import androidx.compose.ui.text.TextStyle"
new_text_import = "import androidx.compose.ui.text.TextLayoutResult\nimport androidx.compose.ui.text.TextStyle"
if old_text_import not in content:
    print("TextStyle import anchor not found!")
    sys.exit(1)
content = content.replace(old_text_import, new_text_import, 1)

old_state = '''    var offsetX by remember { mutableFloatStateOf(0f) }'''
new_state = '''    var offsetX by remember { mutableFloatStateOf(0f) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val annotatedText = remember(message.text, message.sent) { linkifyText(message.text, message.sent) }'''
if old_state not in content:
    print("State anchor not found!")
    sys.exit(1)
content = content.replace(old_state, new_state, 1)

old_tap = '''                        onLongPress = { if (!message.isDeleted) onLongPress(message) },
                        onTap = { if (isSelectionMode) onTap() },'''
new_tap = '''                        onLongPress = { if (!message.isDeleted) onLongPress(message) },
                        onTap = { tapOffset ->
                            if (isSelectionMode) {
                                onTap()
                            } else if (!message.isDeleted && message.text.isNotBlank()) {
                                val layout = textLayoutResult
                                if (layout != null) {
                                    val charOffset = layout.getOffsetForPosition(tapOffset)
                                    val link = annotatedText.getStringAnnotations(LINK_TAG, charOffset, charOffset).firstOrNull()
                                    if (link != null) onLinkTap(link.item)
                                }
                            }
                        },'''
if old_tap not in content:
    print("Tap gesture anchor not found!")
    sys.exit(1)
content = content.replace(old_tap, new_tap, 1)

old_clickable = '''                    } else {
                        val annotated = remember(message.text, message.sent) { linkifyText(message.text, message.sent) }
                        ClickableText(
                            text = annotated,
                            style = TextStyle(color = Color.White, fontSize = 15.sp),
                            onClick = { offset ->
                                val link = annotated.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()
                                if (link != null) onLinkTap(link.item)
                            }
                        )
                    }'''
new_clickable = '''                    } else {
                        Text(
                            text = annotatedText,
                            style = TextStyle(color = Color.White, fontSize = 15.sp),
                            onTextLayout = { textLayoutResult = it }
                        )
                    }'''
if old_clickable not in content:
    print("ClickableText anchor not found!")
    sys.exit(1)
content = content.replace(old_clickable, new_clickable, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("MessageBubble.kt patched: text par bhi hold reliably kaam karega")
