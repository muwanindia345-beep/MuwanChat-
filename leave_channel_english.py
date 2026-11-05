#!/usr/bin/env python3
# LEAVE_CH_EN -- GroupChatScreen: "Leave Channel?" popup ka message English mein
# Sirf ek text string badalta hai. Layout, colors, buttons, behavior -- sab same.
# Usage (repo root MuwanChat se):  python3 leave_channel_english.py [--dry-run]
import io, sys

P = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
MARK = "LEAVE_CH_EN"
DRY = "--dry-run" in sys.argv

try:
    with io.open(P, "r", encoding="utf-8", newline="") as f:
        s = f.read()
except FileNotFoundError:
    print("file nahi mili -- repo root (MuwanChat) se chalao"); sys.exit(1)

if MARK in s:
    print("already applied"); sys.exit(0)

OLD = '"Ye channel aapki Broadcast list se hat jayega."'
NEW = '"This channel will be removed from your Broadcast list."'

if s.count(OLD) != 1:
    print("anchor nahi mila / unique nahi (count=%d) -> kuch nahi likha" % s.count(OLD)); sys.exit(1)

if DRY:
    print("dry-run OK: 1 jagah badlegi"); sys.exit(0)

# marker comment same line ke end par -- UI par koi asar nahi
s = s.replace(OLD, NEW, 1)
idx = s.index(NEW)
eol = s.index("\n", idx)
cr = "\r" if s[eol - 1] == "\r" else ""
cut = eol - 1 if cr else eol
s = s[:cut] + "  // " + MARK + cr + s[eol:]

with io.open(P, "w", encoding="utf-8", newline="") as f:
    f.write(s)
print("done: popup message English mein ho gaya")
