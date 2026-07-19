path = "app/src/main/java/com/muwan/muwanchat/screens/FullscreenVideoPlayer.kt"
with open(path) as f:
    content = f.read()

changes = 0
def rep(old, new, label):
    global content, changes
    if old in content:
        content = content.replace(old, new, 1); changes += 1
    else:
        print(f"WARN: {label} anchor not found")

rep(
    '''            if (onSendReply != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .padding(bottom = 64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {''',
    '''            if (onSendReply != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(if (isReplying) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .padding(bottom = 104.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {''',
    "reply row"
)

rep(
    '''                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0x33FFFFFF))
                                .clickable { isReplying = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Reply, contentDescription = "Reply", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reply...", color = Color.White, fontSize = 14.sp)
                        }''',
    '''                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xCC2A2A2A))
                                .clickable { isReplying = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Reply, contentDescription = "Reply", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reply...", color = Color.White, fontSize = 14.sp)
                        }''',
    "collapsed pill"
)

with open(path, "w") as f:
    f.write(content)
print(f"FullscreenVideoPlayer.kt changes: {changes}")
