#!/bin/bash
set -e

# 1. Apply the one remaining (not-yet-applied) patch — nullsafety fix
python3 << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"
with open(path, "r") as f:
    content = f.read()

old = "showRedDot = g.pendingRequests.isNotEmpty(),"
new = "showRedDot = (g.pendingRequests ?: emptyList()).isNotEmpty(),"

assert old in content, "anchor not found"
content = content.replace(old, new)

with open(path, "w") as f:
    f.write(content)
print("GroupInfoScreen.kt null-safety patch applied")
PYEOF

# 2. Delete all patch scripts (root + app/ folder)
find . -iname "patch_*.py" -delete

echo "Done: nullsafety patch applied, all patch_*.py scripts removed."
