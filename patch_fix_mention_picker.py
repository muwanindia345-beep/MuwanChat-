# Mention picker fix — GroupChatScreen.kt
#
# 1) Profile pic missing tha kyunki mention list mein hardcoded letter-avatar
#    (Box + first-letter Text) use ho raha tha, member.avatar (jo backend se
#    aata hai aur GroupInfoScreen.kt mein already sahi use hota hai) kabhi
#    use hi nahi hota tha. Ab shared AvatarView component use hoga — waisa
#    hi jaisa member list mein pehle se hai — asli photo dikhega, photo na
#    ho to letter fallback apne aap.
#
# 2) Background DarkInputBg (0xFF0f3460, navy blue) tha — ab app ke baaki
#    bottom sheets jaisa DarkSheet (0xFF1C1C1E) color, koi aur cheez change
#    nahi hui.
#
# Termux mein repo root (jahan app/ folder hai) se run karo:
#   python patch_fix_mention_picker.py

f = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
s = open(f).read()

old = '''        AnimatedVisibility(visible = showMentionPicker) {
            val members = group?.memberProfiles?.filter { it.uid != myUid } ?: emptyList()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .background(DarkInputBg)
            ) {
                Text(
                    "Mention someone",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn {
                    items(members) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val prefix = if (input.isNotEmpty() && !input.endsWith(" ")) "$input " else input
                                    input = "$prefix@${member.username} "
                                    showMentionPicker = false
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(DarkAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    member.username.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(member.username, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
        }'''
new = '''        AnimatedVisibility(visible = showMentionPicker) {
            val members = group?.memberProfiles?.filter { it.uid != myUid } ?: emptyList()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .background(DarkSheet)
            ) {
                Text(
                    "Mention someone",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn {
                    items(members) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val prefix = if (input.isNotEmpty() && !input.endsWith(" ")) "$input " else input
                                    input = "$prefix@${member.username} "
                                    showMentionPicker = false
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarView(
                                avatarBase64 = member.avatar,
                                fallbackText = member.username,
                                size = 34.dp,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(member.username, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
        }'''
assert old in s, "GroupChatScreen.kt: pattern not found (mention picker block) — file badal chuki hogi, manually check karo"
open(f, "w").write(s.replace(old, new, 1))
print("✅ GroupChatScreen.kt patched — mention picker: real avatar photo + DarkSheet background")
