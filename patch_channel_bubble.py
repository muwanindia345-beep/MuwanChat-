#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import io
import sys

DRY = "--dry-run" in sys.argv
BASE = "app/src/main/java/com/muwan/muwanchat/screens/"
MB = BASE + "MessageBubble.kt"
GC = BASE + "GroupChatScreen.kt"
MARK = "CHANNEL_BUBBLE_PATCH"


def rd(path):
    try:
        with io.open(path, "r", encoding="utf-8", newline="") as f:
            return f.read()
    except FileNotFoundError:
        print("ERROR: %s nahi mili -- script APP repo root se chalao" % path)
        sys.exit(1)


mb = rd(MB)
gc = rd(GC)

if MARK in mb:
    print("Already applied -- kuch nahi karna.")
    sys.exit(0)

edits_mb = [
    (
        "    onCallTap: (ChatMessage) -> Unit = {}\n) {\n    if (message.type == \"system\") {",
        "    onCallTap: (ChatMessage) -> Unit = {},\n    isChannel: Boolean = false // CHANNEL_BUBBLE_PATCH\n) {\n    if (message.type == \"system\") {",
    ),
    (
        "    val isSticker = message.type == \"gif\"\n",
        "    val isSticker = message.type == \"gif\"\n"
        "    // CHANNEL_BUBBLE_PATCH: channel mein SENT/SEEN tick nahi; PENDING/UPLOADING/FAILED rehte hain\n"
        "    val showStatusIcon = !isChannel || message.status == \"PENDING\" ||\n"
        "        message.status == \"UPLOADING\" || message.status == \"FAILED\"\n",
    ),
    (
        "        Column(horizontalAlignment = if (message.sent) Alignment.End else Alignment.Start) {\n",
        "        Column(\n"
        "            modifier = if (isChannel) Modifier.weight(1f) else Modifier,\n"
        "            horizontalAlignment = if (message.sent) Alignment.End else Alignment.Start\n"
        "        ) {\n",
    ),
    (
        "        Box(\n            contentAlignment = if (message.sent) Alignment.BottomEnd else Alignment.BottomStart\n        ) {\n",
        "        Box(\n"
        "            modifier = if (isChannel) Modifier.fillMaxWidth() else Modifier,\n"
        "            contentAlignment = if (message.sent) Alignment.BottomEnd else Alignment.BottomStart\n"
        "        ) {\n",
    ),
    (
        "                .widthIn(max = 280.dp)\n",
        "                .then(if (isChannel && !isSticker) Modifier.fillMaxWidth() else Modifier.widthIn(max = 280.dp))\n",
    ),
    (
        "                                if (message.sent) {\n                                    Spacer(Modifier.width(3.dp))\n                                    val (icon, tint) = when (message.status) {",
        "                                if (message.sent && showStatusIcon) {\n                                    Spacer(Modifier.width(3.dp))\n                                    val (icon, tint) = when (message.status) {",
    ),
    (
        "                    if (message.sent) {\n                        Spacer(Modifier.width(4.dp))\n                        val (icon, tint) = when (message.status) {",
        "                    if (message.sent && showStatusIcon) {\n                        Spacer(Modifier.width(4.dp))\n                        val (icon, tint) = when (message.status) {",
    ),
]

gc_old = (
    "                        bubbleTheme = bubbleTheme\n"
    "                    )\n                }\n            }\n        }\n\n\n"
    "        AnimatedVisibility(visible = replyTo != null)"
)
gc_new = (
    "                        bubbleTheme = bubbleTheme,\n"
    "                        isChannel = group?.isChannel ?: false // CHANNEL_BUBBLE_PATCH\n"
    "                    )\n                }\n            }\n        }\n\n\n"
    "        AnimatedVisibility(visible = replyTo != null)"
)

problems = []
for i, (old, _) in enumerate(edits_mb, 1):
    n = mb.count(old)
    if n != 1:
        problems.append("MessageBubble.kt edit #%d: anchor %d baar mila (1 chahiye)" % (i, n))
n = gc.count(gc_old)
if n != 1:
    problems.append("GroupChatScreen.kt: MessageBubble call anchor %d baar mila (1 chahiye)" % n)
if problems:
    print("ERROR: kuch apply nahi hua (file jaisi thi waisi hai):")
    for p in problems:
        print("  - " + p)
    sys.exit(1)

for old, new in edits_mb:
    mb = mb.replace(old, new, 1)
gc = gc.replace(gc_old, gc_new, 1)

if DRY:
    print("DRY RUN OK -- MessageBubble.kt (7 edits) + GroupChatScreen.kt (1 edit)")
    sys.exit(0)

with io.open(MB, "w", encoding="utf-8", newline="\n") as f:
    f.write(mb)
with io.open(GC, "w", encoding="utf-8", newline="\n") as f:
    f.write(gc)
print("DONE: MessageBubble.kt + GroupChatScreen.kt patched")
