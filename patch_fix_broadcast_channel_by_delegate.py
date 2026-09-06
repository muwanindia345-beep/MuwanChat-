# -*- coding: utf-8 -*-
# Build fix: BroadcastChannelsScreen.kt me `var showMenu by remember { ... }`
# ka `by` delegate syntax kaam karne ke liye `getValue`/`setValue` operator
# extensions import karna zaroori hai -- patch_broadcast_channel_menu.py
# unhe add karna bhool gaya tha (sirf mutableStateOf/remember import kiye
# the), isliye Kotlin compiler "cannot serve as a delegate" error de raha
# tha. Ye patch sirf missing 2 imports add karta hai.

path = "app/src/main/java/com/muwan/muwanchat/screens/BroadcastChannelsScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old = '''import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember'''

new = '''import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue'''

n = src.count(old)
if n != 1:
    raise SystemExit(f"[FAIL] runtime imports block: found {n} matches (expected 1)")
src = src.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Added missing getValue/setValue imports -- 'by remember' delegate will compile now")
