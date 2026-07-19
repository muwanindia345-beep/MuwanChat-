path = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"
with open(path) as f:
    content = f.read()

old = '''                        .offset(
                            x = if (message.sent) (-6).dp else 6.dp,
                            y = (-10).dp
                        )'''
new = '''                        .offset(
                            x = if (message.sent) (-6).dp else 6.dp,
                            y = (-4).dp
                        )'''

count = content.count(old)
if count == 0:
    print("WARN: offset block not found")
else:
    content = content.replace(old, new)
    print(f"Replaced {count} occurrence(s)")

with open(path, "w") as f:
    f.write(content)
