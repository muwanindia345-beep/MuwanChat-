# -*- coding: utf-8 -*-
# Build fix: CreateChannelScreen.kt me `Modifier.clip(CircleShape)` use
# kiya gaya avatar ko circular crop karne ke liye, lekin `clip` modifier
# function khud `androidx.compose.ui.draw.clip` se import hota hai --
# CircleShape/RoundedCornerShape (shapes) import kiye the par `clip`
# (function) miss ho gaya tha. Isliye "Unresolved reference: clip" error.

path = "app/src/main/java/com/muwan/muwanchat/screens/CreateChannelScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old = '''import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier'''

new = '''import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip'''

n = src.count(old)
if n != 1:
    raise SystemExit(f"[FAIL] import block: found {n} matches (expected 1)")
src = src.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Added missing 'androidx.compose.ui.draw.clip' import")
