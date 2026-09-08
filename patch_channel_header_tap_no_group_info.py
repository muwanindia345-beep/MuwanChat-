# -*- coding: utf-8 -*-
# Channel ke header (naam/avatar area) pe tap karne se ab GroupInfoScreen
# nahi khulti -- GroupInfoScreen sirf normal group ke liye hai (Add from
# Contacts / Search Members / Join Requests, jo channel pe apply hi nahi
# hote). Channel ke liye abhi Coming Soon dialog dikhta hai (wahi jo 3-dot
# > Profile se bhi dikhta hai) -- asli alag Channel Profile screen (apni
# settings/edit ke saath) baad me banega.
#
# Requires: patch_group_chat_header_channel_mode.py pehle se laga hona
# (comingSoonFeature is wired for isChannel already from that patch).

path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old = '''                onHeaderTap = {
                    navController.navigate(Screen.GroupInfo.createRoute(groupId))
                },'''

new = '''                onHeaderTap = {
                    if (group?.isChannel == true) {
                        comingSoonFeature = "Channel Profile"
                    } else {
                        navController.navigate(Screen.GroupInfo.createRoute(groupId))
                    }
                },'''

n = src.count(old)
if n != 1:
    raise SystemExit(f"[FAIL] onHeaderTap block: found {n} matches (expected 1)")
src = src.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Tapping a channel's header no longer opens GroupInfoScreen -- shows Coming Soon instead")
