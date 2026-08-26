import os, sys

def find_file(name):
    for root, dirs, files in os.walk("."):
        if name in files:
            return os.path.join(root, name)
    return None

def add_import_if_missing(content, import_line):
    if import_line in content:
        return content
    # Insert after last "import com.muwan.muwanchat." line, else after package line
    lines = content.split("\n")
    last_muwan_import_idx = None
    for i, l in enumerate(lines):
        if l.startswith("import com.muwan.muwanchat."):
            last_muwan_import_idx = i
    if last_muwan_import_idx is not None:
        lines.insert(last_muwan_import_idx + 1, import_line)
    else:
        for i, l in enumerate(lines):
            if l.startswith("package "):
                lines.insert(i + 1, "")
                lines.insert(i + 2, import_line)
                break
    return "\n".join(lines)

IMPORT_LINE = "import com.muwan.muwanchat.DarkSheet"
changed_files = []

# ---- Simple containerColor swap: DarkHeader -> DarkSheet ----
simple_swap_files = {
    "AcceptedUsersScreen.kt": 1,
    "AccountSettingsScreen.kt": 4,
    "GroupInfoScreen.kt": 2,
    "ConversationListScreen.kt": 1,
}

for fname, expected_count in simple_swap_files.items():
    path = find_file(fname)
    if not path:
        print(f"[-] {fname} nahi mili, skip.")
        continue
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    actual_count = content.count("containerColor = DarkHeader,")
    if actual_count == 0:
        print(f"[*] {fname}: already patched ya pattern nahi mila, skip.")
        continue
    content = content.replace("containerColor = DarkHeader,", "containerColor = DarkSheet,")
    content = add_import_if_missing(content, IMPORT_LINE)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[+] {fname}: {actual_count} popup(s) DarkSheet color par set (expected {expected_count})")
    changed_files.append(path)

# ---- ComingSoonDialog.kt: hardcoded navy -> DarkSheet ----
path = find_file("ComingSoonDialog.kt")
if path:
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    old = "containerColor = Color(0xFF16213e),"
    if old in content:
        content = content.replace(old, "containerColor = DarkSheet,")
        content = add_import_if_missing(content, IMPORT_LINE)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print("[+] ComingSoonDialog.kt: DarkSheet color set")
        changed_files.append(path)
    else:
        print("[*] ComingSoonDialog.kt: pattern nahi mila, skip.")
else:
    print("[-] ComingSoonDialog.kt nahi mili.")

# ---- Dialogs jinme koi dark color hi nahi tha: GroupSettingsScreen, ChatScreen, GroupChatScreen ----
insert_targets = {
    "GroupSettingsScreen.kt": "onDismissRequest = { if (!isBusy) showDeleteConfirm = false },",
    "ChatScreen.kt": "onDismissRequest = { showBulkDeleteConfirm = false },",
    "GroupChatScreen.kt": "onDismissRequest = { showBulkDeleteConfirm = false },",
}

extra_params = (
    "\n            containerColor = DarkSheet,"
    "\n            titleContentColor = Color.White,"
    "\n            textContentColor = Color(0xFFCCCCCC),"
)

for fname, anchor in insert_targets.items():
    path = find_file(fname)
    if not path:
        print(f"[-] {fname} nahi mili, skip.")
        continue
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if "containerColor = DarkSheet," in content and anchor not in content.split("containerColor = DarkSheet,")[0][-50:]:
        pass
    count = content.count(anchor)
    if count == 0:
        print(f"[*] {fname}: anchor line nahi mili (already patched?), skip.")
        continue
    if count > 1:
        print(f"[-] {fname}: anchor line {count} baar mili, ambiguous — manual check karo.")
        continue
    if "containerColor = DarkSheet," in content:
        print(f"[*] {fname}: already patched lag raha hai, skip.")
        continue
    content = content.replace(anchor, anchor + extra_params, 1)
    content = add_import_if_missing(content, IMPORT_LINE)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[+] {fname}: popup ko dark theme + white text mil gaya")
    changed_files.append(path)

print(f"\n[+] Total {len(changed_files)} file(s) update hui.")
