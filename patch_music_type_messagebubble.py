import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old_ismedia = '''    val isMedia = message.type == "image" || message.type == "gif" || message.type == "video" || message.type == "audio"'''
new_ismedia = '''    val isMedia = message.type == "image" || message.type == "gif" || message.type == "video" || message.type == "audio" || message.type == "music"'''
if old_ismedia not in content:
    print("isMedia anchor not found!"); sys.exit(1)
content = content.replace(old_ismedia, new_ismedia, 1)
old_render = '''                    "audio" -> message.mediaUrl?.let { url ->
                        AudioMessagePlayer(url = url, sent = message.sent)
                    }'''
new_render = '''                    "audio", "music" -> message.mediaUrl?.let { url ->
                        AudioMessagePlayer(url = url, sent = message.sent)
                    }'''
if old_render not in content:
    print("Audio render anchor not found!"); sys.exit(1)
content = content.replace(old_render, new_render, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("MessageBubble.kt patched: 'music' type bhi AudioMessagePlayer se render hoga")
