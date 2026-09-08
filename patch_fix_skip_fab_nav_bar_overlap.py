# -*- coding: utf-8 -*-
# Skip FAB phone ke gesture/button navigation bar ke peeche overlap ho raha
# tha, kyunki FAB Box ka direct child hai (Column ke bahar), aur Column ka
# systemBarsPadding() FAB pe apply nahi hota. Fix: FAB ke apne modifier me
# hi navigationBarsPadding() add kar diya, taaki wo hamesha nav bar ke upar
# rahe chahe screen kisi bhi device/gesture-mode me chale.

path = "app/src/main/java/com/muwan/muwanchat/screens/AddFromContactsScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old = '''            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }
    }
}'''

new = '''            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
        )
    }
    }
}'''

n = src.count(old)
if n != 1:
    raise SystemExit(f"[FAIL] FAB modifier block: found {n} matches (expected 1)")
src = src.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Skip FAB now sits above the navigation bar instead of overlapping it")
