import sys
path = "app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old = '''            "audio" -> "🎤 Voice message"
            "document" -> "📄 ${fileName ?: "Document"}"'''
new = '''            "audio" -> "🎤 Voice message"
            "music" -> "🎵 ${fileName ?: "Music"}"
            "document" -> "📄 ${fileName ?: "Document"}"'''
if old not in content:
    print("Preview text anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatRepository.kt patched: music preview text alag se 'Voice message' se")
