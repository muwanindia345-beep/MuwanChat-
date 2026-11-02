import sys, os, re

# Usage:
#   python backend_url_patch.py <repo_root>            -> beta + production dono Render par
#   python backend_url_patch.py <repo_root> beta       -> sirf beta (pehle test ke liye, SAFE)
#   python backend_url_patch.py <repo_root> production -> sirf production

NEW_URL = "https://muwan-chat-backend.onrender.com"

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
ONLY = sys.argv[2] if len(sys.argv) > 2 else "all"
if ONLY not in ("all", "beta", "production"):
    print("Galat option '%s'. Use: beta / production (ya khaali chhodo)." % ONLY)
    sys.exit(1)

GRADLE = os.path.join(ROOT, "app/build.gradle.kts")
lines = open(GRADLE, encoding="utf-8").read().split("\n")

flavor = None
changed = []
pat = re.compile(r'(buildConfigField\("String",\s*"BACKEND_URL",\s*"\\")[^"\\]*(\\""\))')

for i, line in enumerate(lines):
    m = re.search(r'create\("(production|beta)"\)', line)
    if m:
        flavor = m.group(1)
        continue
    if "BACKEND_URL" in line and flavor:
        if ONLY != "all" and ONLY != flavor:
            continue
        if NEW_URL in line:
            continue
        new_line, n = pat.subn(lambda mm: mm.group(1) + NEW_URL + mm.group(2), line)
        if n == 1:
            lines[i] = new_line
            changed.append(flavor)

if not changed:
    print("Kuch change nahi hua (shayad pehle se Render URL laga hai, ya line match nahi hui).")
    sys.exit(0)

open(GRADLE, "w", encoding="utf-8").write("\n".join(lines))
print("OK: BACKEND_URL -> %s  (flavors: %s)" % (NEW_URL, ", ".join(changed)))
