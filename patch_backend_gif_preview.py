import sys

files = [
    ("routes/chat.js", '''  switch (msg.type) {
    case 'image': return '📷 Photo';
    case 'video': return '🎥 Video';
    case 'audio': return '🎤 Voice message';
    case 'document': return `📎 ${msg.file_name || 'Document'}`;
    default: return msg.content;
  }''', '''  switch (msg.type) {
    case 'image': return '📷 Photo';
    case 'gif': return '🎬 GIF';
    case 'video': return '🎥 Video';
    case 'audio': return '🎤 Voice message';
    case 'document': return `📎 ${msg.file_name || 'Document'}`;
    default: return msg.content;
  }'''),
    ("socket/chat.js", '''                switch (msg.type) {
                  case 'image': return '📷 Photo';
                  case 'video': return '🎥 Video';
                  case 'audio': return '🎤 Voice message';
                  case 'document': return `📄 ${msg.file_name || 'Document'}`;
                  default: return content;
                }''', '''                switch (msg.type) {
                  case 'image': return '📷 Photo';
                  case 'gif': return '🎬 GIF';
                  case 'video': return '🎥 Video';
                  case 'audio': return '🎤 Voice message';
                  case 'document': return `📄 ${msg.file_name || 'Document'}`;
                  default: return content;
                }'''),
]

any_changed = False
for path, old, new in files:
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if old in content:
        content = content.replace(old, new)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"[OK] {path} patched")
        any_changed = True
    elif new in content:
        print(f"[SKIP] {path} already patched")
    else:
        print(f"[WARN] {path} — pattern not found, check manually")

if not any_changed:
    print("\\nNothing changed.")
