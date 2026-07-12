import io
path = "app/src/main/java/com/muwan/muwanchat/data/MuwanChatDb.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old = "    version = 12,"
new = "    version = 13,"

assert content.count(old) == 1, "match failed"
content = content.replace(old, new)
with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched MuwanChatDb.kt (v12 -> v13, fallbackToDestructiveMigration already set)")
