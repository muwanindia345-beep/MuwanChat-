import re

# ---- 1-on-1 Chat Header ----
path1 = "app/src/main/java/com/muwan/muwanchat/screens/ChatHeader.kt"
with open(path1, "r", encoding="utf-8") as f:
    c1 = f.read()

old1 = """                Text(
                    receiverUsername,
                    color = DarkAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )"""
new1 = """                Text(
                    receiverUsername,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )"""

old2 = """            IconButton(onClick = onVideoCall) {
                Icon(Icons.Filled.VideoCall, contentDescription = "Video",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onVoiceCall) {
                Icon(Icons.Filled.Call, contentDescription = "Call",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
            Box {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu",
                        tint = DarkAccent, modifier = Modifier.size(22.dp))
                }"""
new2 = """            IconButton(onClick = onVideoCall) {
                Icon(Icons.Filled.VideoCall, contentDescription = "Video",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onVoiceCall) {
                Icon(Icons.Filled.Call, contentDescription = "Call",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Box {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu",
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }"""

ok1 = old1 in c1
ok2 = old2 in c1
if ok1:
    c1 = c1.replace(old1, new1, 1)
if ok2:
    c1 = c1.replace(old2, new2, 1)
with open(path1, "w", encoding="utf-8") as f:
    f.write(c1)
print(f"ChatHeader.kt -> name patched: {ok1}, icons patched: {ok2}")

# ---- Group Chat Header ----
path2 = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with open(path2, "r", encoding="utf-8") as f:
    c2 = f.read()

old3 = """                Text(
                    groupName, color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )"""
new3 = """                Text(
                    groupName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )"""

old4 = """            IconButton(onClick = onVideoCall) {
                Icon(androidx.compose.material.icons.Icons.Filled.VideoCall, contentDescription = "Video",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onVoiceCall) {
                Icon(androidx.compose.material.icons.Icons.Filled.Call, contentDescription = "Call",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onMenuClick) {
                Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "Menu",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }"""
new4 = """            IconButton(onClick = onVideoCall) {
                Icon(androidx.compose.material.icons.Icons.Filled.VideoCall, contentDescription = "Video",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onVoiceCall) {
                Icon(androidx.compose.material.icons.Icons.Filled.Call, contentDescription = "Call",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onMenuClick) {
                Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "Menu",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }"""

ok3 = old3 in c2
ok4 = old4 in c2
if ok3:
    c2 = c2.replace(old3, new3, 1)
if ok4:
    c2 = c2.replace(old4, new4, 1)
with open(path2, "w", encoding="utf-8") as f:
    f.write(c2)
print(f"GroupChatScreen.kt -> name patched: {ok3}, icons patched: {ok4}")
