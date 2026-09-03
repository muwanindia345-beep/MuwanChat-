def apply(path, old, new, label):
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    n = src.count(old)
    if n != 1:
        raise SystemExit(f"[FAIL] {label} ({path}): found {n} matches (expected 1)")
    src = src.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print(f"[OK] {label}")

# ---------------------------------------------------------------------------
# FullscreenImageViewer.kt — photo fullscreen se "Reply..." bar hatao
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/FullscreenImageViewer.kt",
'''            // ── Reply bar: photo ke niche hi tag + inline text field ──
            if (onSendReply != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(if (isReplying) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isReplying) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(replyFocusRequester),
                            placeholder = { Text("Reply...", color = Color(0xFF888888)) },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DarkAccent,
                                unfocusedBorderColor = Color(0xFF555555),
                                cursorColor = DarkAccent
                            ),
                            maxLines = 3
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val text = replyText.trim()
                                if (text.isNotBlank()) {
                                    onSendReply(text)
                                    replyText = ""
                                    isReplying = false
                                }
                            },
                            modifier = Modifier
                                .background(DarkAccent, CircleShape)
                                .size(42.dp)
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send reply", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Row(
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
                        }
                    }
                }
            }''',
'''            // Reply bar hata diya gaya hai — fullscreen viewer se reply nahi
            // ho sakta ab; baaki sab (save, close, zoom) same rahega.
''',
    "FullscreenImageViewer.kt: remove Reply bar"
)

# ---------------------------------------------------------------------------
# FullscreenVideoPlayer.kt — video fullscreen se "Reply..." bar hatao
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/FullscreenVideoPlayer.kt",
'''            if (onSendReply != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(if (isReplying) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .padding(bottom = 104.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isReplying) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(replyFocusRequester),
                            placeholder = { Text("Reply...", color = Color(0xFF888888)) },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DarkAccent,
                                unfocusedBorderColor = Color(0xFF555555),
                                cursorColor = DarkAccent
                            ),
                            maxLines = 3
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val text = replyText.trim()
                                if (text.isNotBlank()) {
                                    onSendReply(text)
                                    replyText = ""
                                    isReplying = false
                                }
                            },
                            modifier = Modifier
                                .background(DarkAccent, CircleShape)
                                .size(42.dp)
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send reply", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Row(
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
                        }
                    }
                }
            }''',
'''            // Reply bar hata diya gaya hai — fullscreen viewer se reply nahi
            // ho sakta ab; baaki sab (save, close, playback controls) same rahega.
''',
    "FullscreenVideoPlayer.kt: remove Reply bar"
)

print("\n[DONE] Reply option fullscreen image + video viewers dono se hata diya gaya")
