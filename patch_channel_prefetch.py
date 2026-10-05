#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import io
import sys

PATH = "app/src/main/java/com/muwan/muwanchat/screens/BroadcastChannelsScreen.kt"
DRY = "--dry-run" in sys.argv

IMPORT_ANCHOR = "import com.muwan.muwanchat.data.ChannelsCacheEntity\n"
IMPORT_NEW = IMPORT_ANCHOR + "import com.muwan.muwanchat.data.ChatRepository\n"

CALL_ANCHOR = (
    "                    db.channelsCacheDao().upsert(ChannelsCacheEntity(json = gson.toJson(fresh)))\n"
)
CALL_NEW = CALL_ANCHOR + (
    "                    // CHANNEL_PREFETCH_PATCH: channels ke messages bhi background mein local kar lo\n"
    "                    ChatRepository.prefetchMessagesInBackground(db, token, fresh)\n"
)

try:
    with io.open(PATH, "r", encoding="utf-8", newline="") as f:
        src = f.read()
except FileNotFoundError:
    print("ERROR: %s nahi mili -- script APP repo root se chalao" % PATH)
    sys.exit(1)

if "CHANNEL_PREFETCH_PATCH" in src:
    print("Already applied -- kuch nahi karna.")
    sys.exit(0)

problems = []
if src.count(CALL_ANCHOR) != 1:
    problems.append("upsert line %d baar mili (1 honi chahiye)" % src.count(CALL_ANCHOR))
if src.count(IMPORT_ANCHOR) != 1:
    problems.append("ChannelsCacheEntity import %d baar mila (1 hona chahiye)" % src.count(IMPORT_ANCHOR))
if problems:
    print("ERROR: patch apply nahi hua:")
    for p in problems:
        print("  - " + p)
    sys.exit(1)

if "import com.muwan.muwanchat.data.ChatRepository\n" not in src:
    src = src.replace(IMPORT_ANCHOR, IMPORT_NEW, 1)
src = src.replace(CALL_ANCHOR, CALL_NEW, 1)

if DRY:
    print("DRY RUN OK -- 2 changes hote (import + prefetch call)")
    sys.exit(0)

with io.open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)
print("DONE: %s patched (import + prefetch call)" % PATH)
