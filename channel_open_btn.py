#!/usr/bin/env python3
# CHANNEL_OPEN_BTN -- JoinGroupScreen: channel ke liye button text "Open Channel" (group ke liye "Open Chat" hi rahega)
# Sirf text badalta hai. Layout, colors, navigation, behavior -- sab same.
# Usage (repo root MuwanChat se):  python3 channel_open_btn.py [--dry-run]
import io, sys

P = "app/src/main/java/com/muwan/muwanchat/screens/JoinGroupScreen.kt"
MARK = "CHANNEL_OPEN_BTN"
DRY = "--dry-run" in sys.argv

try:
    with io.open(P, "r", encoding="utf-8", newline="") as f:
        s = f.read()
except FileNotFoundError:
    print("file nahi mili -- repo root (MuwanChat) se chalao"); sys.exit(1)

if MARK in s:
    print("already applied"); sys.exit(0)

OLD = 'Text("Open Chat", color = Color.White)'

# (unique anchor line, channel flag jo us branch mein available hai)
TARGETS = [
    ("Screen.GroupChat.createRoute(s.data.id, s.data.name)", "s.data.isChannel"),   # alreadyMember branch
    ("Screen.GroupChat.createRoute(s.groupId, s.groupName)", "s.isChannel"),        # Joined branch (screenshot wala)
]

lines = s.splitlines(keepends=True)
done = 0
for anchor, flag in TARGETS:
    hits = [i for i, l in enumerate(lines) if anchor in l]
    if len(hits) != 1:
        print("anchor nahi mila / unique nahi: " + anchor + " -> kuch nahi likha"); sys.exit(1)
    j = next((k for k in range(hits[0], min(hits[0] + 12, len(lines))) if OLD in lines[k]), None)
    if j is None:
        print("button text anchor ke paas nahi mila: " + anchor + " -> kuch nahi likha"); sys.exit(1)
    new = 'Text(if (' + flag + ') "Open Channel" else "Open Chat", color = Color.White)'
    lines[j] = lines[j].replace(OLD, new, 1)
    if done == 0:
        lines[j] = lines[j].rstrip("\r\n") + "  // " + MARK + ("\r\n" if lines[j].endswith("\r\n") else "\n")
    done += 1

out = "".join(lines)
if DRY:
    print("dry-run OK: %d jagah badlegi" % done); sys.exit(0)
with io.open(P, "w", encoding="utf-8", newline="") as f:
    f.write(out)
print("done: %d jagah 'Open Channel' (sirf channel ke liye) lag gaya" % done)
