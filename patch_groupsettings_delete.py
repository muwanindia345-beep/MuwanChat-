path = "app/src/main/java/com/muwan/muwanchat/screens/GroupSettingsScreen.kt"
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
    "import com.muwan.muwanchat.data.AuthDataStore\n",
    "import com.muwan.muwanchat.data.AuthDataStore\nimport com.muwan.muwanchat.data.ChatRepository\nimport com.muwan.muwanchat.data.MuwanChatDb\nimport com.muwan.muwanchat.navigation.Screen\n",
    "imports"
)

rep(
    "    val isAdmin = group?.admins?.contains(myUid) == true",
    "    val isAdmin = group?.admins?.contains(myUid) == true\n    val isOwner = group?.owner == myUid\n    var showDeleteConfirm by remember { mutableStateOf(false) }",
    "state"
)

rep(
    '''            isBusy = false
        }
    }

    Column(''',
    '''            isBusy = false
        }
    }

    fun deleteGroupPermanently() {
        scope.launch {
            isBusy = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.deleteGroup("Bearer $token", groupId)
                if (res.isSuccessful) {
                    val db = MuwanChatDb.get(context, myUid)
                    ChatRepository.deleteChatsLocally(db, setOf(groupId))
                    navController.navigate(Screen.ConversationList.route) {
                        popUpTo(Screen.ConversationList.route) { inclusive = true }
                    }
                } else {
                    Toast.makeText(context, "Group delete nahi hua", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            isBusy = false
            showDeleteConfirm = false
        }
    }

    Column(''',
    "deleteGroupPermanently fn"
)

rep(
    '''                SettingsToggleRow(
                    icon = Icons.Filled.NotificationsOff,
                    label = "Mute this group",
                    subtitle = "You won't get push notifications for new messages",
                    checked = muted,
                    enabled = true,
                    onCheckedChange = { toggleMute(it) }
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}''',
    '''                SettingsToggleRow(
                    icon = Icons.Filled.NotificationsOff,
                    label = "Mute this group",
                    subtitle = "You won't get push notifications for new messages",
                    checked = muted,
                    enabled = true,
                    onCheckedChange = { toggleMute(it) }
                )

                if (isOwner) {
                    Spacer(Modifier.height(12.dp))
                    SettingsSectionLabel("DANGER ZONE")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isBusy) { showDeleteConfirm = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = Color(0xFFFF3B30))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Delete Group", color = Color(0xFFFF3B30), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Permanently deletes this group and all messages for everyone",
                                color = Color(0xFF888888),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showDeleteConfirm = false },
            title = { Text("Delete Group?") },
            text = { Text("This permanently deletes \\"${group?.name}\\" and all its messages for every member. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { deleteGroupPermanently() }, enabled = !isBusy) {
                    Text("Delete", color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !isBusy) {
                    Text("Cancel")
                }
            }
        )
    }
}''',
    "danger zone + confirm dialog"
)

with open(path, "w") as f:
    f.write(content)
print(f"GroupSettingsScreen.kt changes: {changes}")
