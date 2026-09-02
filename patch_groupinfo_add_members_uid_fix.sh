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
# Run from project root (MuwanChat--main folder):
#   bash patch_groupinfo_add_members_uid_fix.sh

set -e

TARGET_FILE="app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"

if [ ! -f "$TARGET_FILE" ]; then
    echo "ERROR: $TARGET_FILE not found. Run this script from the MuwanChat--main root folder."
    exit 1
fi

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
