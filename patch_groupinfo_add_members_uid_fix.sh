#!/data/data/com.termux/files/usr/bin/bash
# patch_groupinfo_add_members_uid_fix.sh
# ROOT CAUSE FIX: Release build failing with:
#   e: GroupInfoScreen.kt:618:66 Type mismatch: inferred type is Unit but String was expected
#   e: GroupInfoScreen.kt:618:85 Unresolved reference: uid
#   e: GroupInfoScreen.kt:627:66 Type mismatch: inferred type is Unit but String was expected
#   e: GroupInfoScreen.kt:627:85 Unresolved reference: uid
#
# `members` on the group model (ChatApi.kt) is declared as
#   val members: List<String>
# i.e. it's already a list of uid strings, not member objects. The two
# call sites in GroupInfoScreen.kt (Add from Contacts / Search Members)
# were doing `g.members.map { it.uid }`, treating each `it` (a String)
# as if it had a `.uid` property, which doesn't exist -> compile error.
#
# FIX: pass g.members straight through, no .map { it.uid } needed.
#
# THIS VERSION ALSO COMMITS + PUSHES THE FIX. Last CI run (#119) failed
# with the exact same line/column as before, which means the patch was
# applied locally but never pushed to GitHub — Actions builds whatever
# is on the remote branch, not what's sitting on your phone. So this
# script now stages, commits, and pushes automatically after patching.
#
# Run from INSIDE YOUR CLONED REPO (the folder that has a .git folder):
#   cd MuwanChat-              <- your real clone, not a fresh unzip
#   bash patch_groupinfo_add_members_uid_fix.sh

set -e

TARGET_FILE="app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"

# ── 0. Sanity checks ──
if [ ! -d ".git" ]; then
    echo "ERROR: No .git folder here. Run this from inside your actual cloned"
    echo "       repo (the one connected to GitHub), not a fresh unzip of the"
    echo "       source — otherwise there's nothing to push."
    exit 1
fi

if [ ! -f "$TARGET_FILE" ]; then
    echo "ERROR: $TARGET_FILE not found. Run this script from the repo root."
    exit 1
fi

# ── 1. Apply the fix (idempotent) ──
python3 - << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = "GroupMemberSelection.setExistingUids(g.members.map { it.uid })"
new = "GroupMemberSelection.setExistingUids(g.members)"

count = content.count(old)
if count == 0:
    if content.count(new) > 0:
        print("SKIP: GroupInfoScreen.kt already patched")
    else:
        print("WARN: anchor text not found — check GroupInfoScreen.kt manually (file may have changed)")
else:
    content = content.replace(old, new)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Patched: GroupInfoScreen.kt -> fixed {count} call site(s) of setExistingUids()")
PYEOF

echo ""
echo "Verifying brace/paren balance..."
python3 -c "
content = open('$TARGET_FILE').read()
o, c = content.count('{'), content.count('}')
po, pc = content.count('('), content.count(')')
status = 'OK' if (o == c and po == pc) else 'MISMATCH!'
print(f'$TARGET_FILE -> braces {o}/{c}, parens {po}/{pc} -> {status}')
"

# ── 2. Commit + push (this is the step that was missing last time) ──
echo ""
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
echo "Current branch: $CURRENT_BRANCH"

git add "$TARGET_FILE"

if git diff --cached --quiet; then
    echo "Nothing new to commit — GroupInfoScreen.kt already matches the fixed version in this repo."
    echo "If CI is still failing on the old lines, your GitHub remote is out of date:"
    echo "  git log origin/$CURRENT_BRANCH -1 -- $TARGET_FILE"
    echo "  git push"
else
    git commit -m "Fix setExistingUids type mismatch in GroupInfoScreen (g.members is already List<String>)"
    echo "Committed."
    echo "Pushing to origin/$CURRENT_BRANCH ..."
    git push origin "$CURRENT_BRANCH"
    echo "Pushed. The release workflow should re-trigger automatically."
fi

echo ""
echo "Double-check on GitHub: https://github.com/<your-username>/<repo>/blob/$CURRENT_BRANCH/$TARGET_FILE#L618"
