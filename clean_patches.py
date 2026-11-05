#!/usr/bin/env python3
"""
clean_patches.py -- MuwanChat APK repo ya backend repo ke purane one-time patch scripts saaf karta hai.

Usage (repo root se, dono repos mein same script):
    python3 clean_patches.py             # sirf list dikhata hai (dry-run, kuch nahi badalta)
    python3 clean_patches.py --apply     # asli safai

Safe kaise hai:
  * Pehle check: patch ka MARK/TAG (jaise GUEST_P2C) source code mein mil raha ho == patch lag chuka hai -> hatega.
    Marker source mein na mile == patch lagna baaki hai -> KEEP (hatega nahi).
  * Jo patch mein marker hi nahi (purane patches), wo "unverified" dikhte hain; hatte hain, par pehle backup hota hai.
  * Har cheez hatane se pehle ../_patch_archive_<time>/ mein copy ho jaati hai (repo ke bahar). Git history alag se.
  * Source code (app/, routes/, lib/, socket/, index.js) ko ye script chhuta bhi nahi.
  * Commit/push ye khud nahi karta -- last mein command batata hai.
"""
import os, re, sys, shutil, subprocess, time

APPLY = "--apply" in sys.argv
args = [a for a in sys.argv[1:] if not a.startswith("--")]
ROOT = os.path.abspath(args[0]) if args else os.getcwd()

IS_APK = os.path.isdir(os.path.join(ROOT, "app", "src"))
IS_BACKEND = os.path.isfile(os.path.join(ROOT, "index.js")) and os.path.isdir(os.path.join(ROOT, "routes"))
if not (IS_APK or IS_BACKEND):
    print("FAIL: ye na APK repo lagta hai na backend repo. Repo root se chalao.")
    sys.exit(1)

SRC_DIRS = ["app/src"] if IS_APK else ["routes", "lib", "socket"]
SRC_FILES = [] if IS_APK else ["index.js"]
SRC_EXT = (".kt", ".xml", ".kts", ".pro", ".js", ".json")

ROOT_PATTERNS = [
    r".*_patch\.py", r".*_fix\.py", r"patch_.*\.(py|sh)", r"fix_.*\.py", r"revert_.*\.py",
    r"guest_backend_v\d+\.py", r"cleanup_.*\.sh",
]
KEEP_ALWAYS = {"clean_patches.py", "bugfix_backend_v1.py"}  # bugfix_backend_v1 pehle se jaan-boojh ke rakha tha

def read(p):
    try:
        with open(p, encoding="utf-8", errors="ignore") as f:
            return f.read()
    except OSError:
        return ""

def source_blob():
    parts = []
    for d in SRC_DIRS:
        for dp, dn, fn in os.walk(os.path.join(ROOT, d)):
            dn[:] = [x for x in dn if x not in ("node_modules", "build", ".git")]
            for n in fn:
                if n.endswith(SRC_EXT):
                    parts.append(read(os.path.join(dp, n)))
    for f in SRC_FILES:
        parts.append(read(os.path.join(ROOT, f)))
    return "\n".join(parts)

BLOB = source_blob()

def marker_of(path):
    m = re.search(r'^(?:MARK|TAG|MARKER)\s*=\s*["\']([A-Za-z0-9_]+)["\']', read(path), re.M)
    return m.group(1) if m else None

remove, keep, unverified = [], [], []

# 1) root-level patch scripts
for name in sorted(os.listdir(ROOT)):
    p = os.path.join(ROOT, name)
    if not os.path.isfile(p) or name in KEEP_ALWAYS:
        continue
    if not any(re.fullmatch(pat, name) for pat in ROOT_PATTERNS):
        continue
    mk = marker_of(p)
    if mk is None:
        unverified.append(name)
        remove.append(name)
    elif mk in BLOB:
        remove.append(name)
    else:
        keep.append((name, mk))

# 2) backup folders / .bak files / junk
JUNK_DIRS = [".guest_ui_backup"]
for d in JUNK_DIRS:
    if os.path.isdir(os.path.join(ROOT, d)):
        remove.append(d + "/")
for dp, dn, fn in os.walk(ROOT):
    dn[:] = [x for x in dn if x not in ("node_modules", "build", ".git", ".gradle")]
    for n in fn:
        if ".bak" in n or n.endswith((".orig", ".rej", "~")):
            remove.append(os.path.relpath(os.path.join(dp, n), ROOT))
for n in os.listdir(ROOT):
    if re.fullmatch(r"ngrok.*\.(tgz|zip|tar\.gz)", n):
        remove.append(n)

remove = sorted(set(remove))

print("Repo      :", ROOT, "(APK)" if IS_APK else "(backend)")
print("Mode      :", "APPLY" if APPLY else "DRY-RUN (kuch nahi badlega)")
print()
print("HATENGE (%d):" % len(remove))
for r in remove:
    tag = "  [unverified: marker nahi]" if r in unverified else ""
    print("  - " + r + tag)
if keep:
    print("\nKEEP -- ye patch abhi lage nahi lagte (marker source mein nahi mila):")
    for n, mk in keep:
        print("  ! %s  (marker %s)" % (n, mk))
if not remove:
    print("Kuch saaf karne ko nahi mila.")
    sys.exit(0)

if not APPLY:
    print("\nSab theek lage to chalao:  python3 clean_patches.py --apply")
    sys.exit(0)

# ---- apply ----
stamp = time.strftime("%Y%m%d_%H%M%S")
ARCH = os.path.join(os.path.dirname(ROOT), "_patch_archive_" + stamp)
os.makedirs(ARCH, exist_ok=True)

def tracked(rel):
    try:
        r = subprocess.run(["git", "ls-files", "--error-unmatch", rel.rstrip("/")],
                           cwd=ROOT, capture_output=True)
        return r.returncode == 0
    except OSError:
        return False

for rel in remove:
    src = os.path.join(ROOT, rel.rstrip("/"))
    dst = os.path.join(ARCH, rel.rstrip("/"))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if os.path.isdir(src):
        shutil.copytree(src, dst, dirs_exist_ok=True)
    else:
        shutil.copy2(src, dst)
    if tracked(rel):
        subprocess.run(["git", "rm", "-r", "-q", "-f", "--", rel.rstrip("/")], cwd=ROOT)
    if os.path.exists(src):
        shutil.rmtree(src) if os.path.isdir(src) else os.remove(src)

# .gitignore mein patterns taaki dobara repo mein na ghusein
GI = os.path.join(ROOT, ".gitignore")
want = ["*_patch.py", "*_fix.py", "guest_backend_v*.py", ".guest_ui_backup/", "ngrok*.tgz", "*.bak*"]
cur = read(GI)
add = [w for w in want if w not in cur.split()]
if add:
    with open(GI, "a", encoding="utf-8") as f:
        if cur and not cur.endswith("\n"):
            f.write("\n")
        f.write("\n# one-off patch scripts (auto-added by clean_patches.py)\n" + "\n".join(add) + "\n")

print("\nDONE. %d cheezein hataayi. Backup: %s" % (len(remove), ARCH))
print("Ab:")
print("   git add -A && git commit -m \"chore: remove applied patch scripts\" && git push")
