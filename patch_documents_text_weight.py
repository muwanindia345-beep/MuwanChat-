p = "app/src/main/java/com/muwan/muwanchat/screens/MediaScreen.kt"
s = open(p, encoding="utf-8").read()

old = '''                Icon(Icons.Filled.Description, contentDescription = null, tint = DarkAccent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    msg.fileName ?: "Document",
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )'''

new = '''                Icon(Icons.Filled.Description, contentDescription = null, tint = DarkAccent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    msg.fileName ?: "Document",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )'''

if old in s:
    s = s.replace(old, new)
    open(p, "w", encoding="utf-8").write(s)
    print("MediaScreen.kt: Document filename Text now uses weight(1f) + softWrap=false")
elif "modifier = Modifier.weight(1f)" in s and "softWrap = false" in s:
    print("MediaScreen.kt: already fixed, no change needed")
else:
    print("WARNING: anchor not found, check manually")

print("DONE")
