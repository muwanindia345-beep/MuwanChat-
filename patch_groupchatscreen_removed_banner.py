path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with open(path) as f:
    content = f.read()

# --- 1. socket event handling: GroupRemoved case add karo (roomId match) ---
old1 = '''                is SocketEvent.MessagesSeen -> {
                    if (event.roomId == groupId) {
                        db.messageDao().markMySentAsSeen(groupId, myUid)
                    }
                }'''
new1 = '''                is SocketEvent.GroupRemoved -> {
                    if (event.roomId == groupId && !event.selfLeave) {
                        db.conversationDao().markRemoved(
                            groupId,
                            event.removedByUsername ?: "Admin"
                        )
                    }
                }
                is SocketEvent.MessagesSeen -> {
                    if (event.roomId == groupId) {
                        db.messageDao().markMySentAsSeen(groupId, myUid)
                    }
                }'''
assert old1 in content, "MessagesSeen marker not found"
content = content.replace(old1, new1, 1)

# --- 2. input bar: removed hone par static banner dikhao, warna normal ChatInputBar ---
old2 = '''        AnimatedVisibility(visible = showEmojiPicker) {
            EmojiPickerRow { emoji -> input += emoji }
        }
        ChatInputBar(
            input = input,
            onInputChange = {
                input = it
                if (showEmojiPicker) showEmojiPicker = false

                if (AppSocketManager.isConnected) {
                    if (it.isNotBlank()) {
                        AppSocketManager.sendGroupTyping(groupId)
                        typingJob?.cancel()
                        typingJob = scope.launch {
                            delay(2500)
                            AppSocketManager.sendGroupStopTyping(groupId)
                        }
                    } else {
                        typingJob?.cancel()
                        AppSocketManager.sendGroupStopTyping(groupId)
                    }
                }
            },
            showEmojiPicker = showEmojiPicker,
            onToggleEmojiPicker = {
                showEmojiPicker = !showEmojiPicker
                if (showEmojiPicker) keyboardController?.hide() else keyboardController?.show()
            },
            onPickImage = { showMediaSheet = true },
            onSend = { sendMessage() },
            onGifReceived = { uri, _, release ->
                scope.launch {
                    uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db, skipCompression = true, setUploading = { uploadingMedia = it })
                    release()
                }
            },
            onVoiceMessage = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    showVoiceRecorder = true
                } else {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )
    }'''

new2 = '''        AnimatedVisibility(visible = showEmojiPicker) {
            EmojiPickerRow { emoji -> input += emoji }
        }
        if (conversationEntity?.isRemoved == true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkInputBg)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "You were removed from this group by @${conversationEntity?.removedByUsername ?: "Admin"}",
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            ChatInputBar(
                input = input,
                onInputChange = {
                    input = it
                    if (showEmojiPicker) showEmojiPicker = false

                    if (AppSocketManager.isConnected) {
                        if (it.isNotBlank()) {
                            AppSocketManager.sendGroupTyping(groupId)
                            typingJob?.cancel()
                            typingJob = scope.launch {
                                delay(2500)
                                AppSocketManager.sendGroupStopTyping(groupId)
                            }
                        } else {
                            typingJob?.cancel()
                            AppSocketManager.sendGroupStopTyping(groupId)
                        }
                    }
                },
                showEmojiPicker = showEmojiPicker,
                onToggleEmojiPicker = {
                    showEmojiPicker = !showEmojiPicker
                    if (showEmojiPicker) keyboardController?.hide() else keyboardController?.show()
                },
                onPickImage = { showMediaSheet = true },
                onSend = { sendMessage() },
                onGifReceived = { uri, _, release ->
                    scope.launch {
                        uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db, skipCompression = true, setUploading = { uploadingMedia = it })
                        release()
                    }
                },
                onVoiceMessage = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        showVoiceRecorder = true
                    } else {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
    }'''

assert old2 in content, "ChatInputBar block not found"
content = content.replace(old2, new2, 1)

with open(path, "w") as f:
    f.write(content)
print("GroupChatScreen.kt patched: live GroupRemoved handling + input-bar banner switch")
