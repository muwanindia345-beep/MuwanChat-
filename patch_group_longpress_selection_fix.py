#!/usr/bin/env python3
"""
Fix: GroupChatScreen mein long-press pe sirf reaction bar aata tha,
selection toolbar (edit/pin/forward/delete) nahi aata tha -- jabki
1-on-1 ChatScreen mein dono aate hain. Ab dono screens ka behavior
match kar diya.
"""
import re
import sys

FILE = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

with open(FILE, "r", encoding="utf-8") as f:
    content = f.read()

# --- Fix 1: onLongPress block ---
old_longpress = """                        onLongPress = {
                            if (!isSelectionMode) {
                                if (it.isDeleted) {
                                    // Deleted message pe react nahi ho sakta -- yahan asli
                                    // selection mode chahiye (bulk actions ke liye)
                                    isSelectionMode = true
                                    selectedMessageIds = setOf(it.id)
                                } else {
                                    // Sirf quick-react ke liye poora selection-mode (jo header
                                    // badal deta hai aur list reflow/jump karti hai) enable nahi
                                    // karte -- sirf reaction dialog kholte hain.
                                    selectedMessageIds = setOf(it.id)
                                    showReactionPicker = true
                                }
                            }
                        },"""

new_longpress = """                        onLongPress = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedMessageIds = setOf(it.id)
                                if (!it.isDeleted) showReactionPicker = true
                            }
                        },"""

if old_longpress not in content:
    print("❌ FAIL: onLongPress old block nahi mila -- file already patched ya modified hai.")
    sys.exit(1)

content = content.replace(old_longpress, new_longpress, 1)
print("✅ onLongPress block patched")

# --- Fix 2: reaction dialog dismiss ka dead code ---
old_dismiss = """            showReactionPicker = false
            showCustomEmojiField = false
            customEmojiInput = ""
            if (!isSelectionMode) selectedMessageIds = emptySet()
        }) {"""

new_dismiss = """            showReactionPicker = false
            showCustomEmojiField = false
            customEmojiInput = ""
        }) {"""

if old_dismiss not in content:
    print("❌ FAIL: dismiss block nahi mila -- file already patched ya modified hai.")
    sys.exit(1)

content = content.replace(old_dismiss, new_dismiss, 1)
print("✅ dismiss block cleaned")

with open(FILE, "w", encoding="utf-8") as f:
    f.write(content)

print("🎉 Patch applied successfully: GroupChatScreen ab ChatScreen jaisa long-press selection dega.")
