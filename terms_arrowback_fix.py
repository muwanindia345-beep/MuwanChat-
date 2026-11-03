import sys, os

# Usage (Termux, repo root MuwanChat se):
#   python terms_arrowback_fix.py .
# Fix: TermsPolicyScreen.kt:62 "Unresolved reference: ArrowBack"
# Wajah: ArrowBack ek extension property hai, uska import chahiye tha.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
F = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat/screens/TermsPolicyScreen.kt")

if not os.path.isfile(F):
    print("FAIL: file nahi mili: %s (repo root se chalao)" % F)
    sys.exit(1)

s = open(F, encoding="utf-8").read()

if "ARROWBACK_FIX" in s:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: matched %d times" % (name, n))
        sys.exit(1)
    return s.replace(old, new)

s = rep(s,
"import androidx.compose.material3.*\n",
"import androidx.compose.material.icons.Icons  // ARROWBACK_FIX\n"
"import androidx.compose.material.icons.filled.ArrowBack  // ARROWBACK_FIX\n"
"import androidx.compose.material3.*\n",
"imports")

s = rep(s,
"Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, contentDescription",
"Icon(Icons.Filled.ArrowBack, contentDescription",
"icon call")

open(F, "w", encoding="utf-8").write(s)
print("OK: TermsPolicyScreen.kt fix ho gaya.")
