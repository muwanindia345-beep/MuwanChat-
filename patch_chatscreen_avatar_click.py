import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = '''                onVideoCall = { comingSoonFeature = "📹 Video Call" },
                onVoiceCall = { comingSoonFeature = "📞 Voice Call" },
                onMenuClick = { showMenuSheet = true }
            )'''
new = '''                onVideoCall = { comingSoonFeature = "📹 Video Call" },
                onVoiceCall = { comingSoonFeature = "📞 Voice Call" },
                onMenuClick = { showMenuSheet = true },
                onAvatarClick = {
                    AvatarViewerSelection.set(conversationEntity?.avatar, receiverUsername)
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.ViewAvatar.route)
                }
            )'''
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatScreen.kt patched: avatar tap opens ViewAvatarScreen")
