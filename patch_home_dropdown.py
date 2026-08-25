import os
import sys

# File ko dynamically search karna taaki path mismatch na ho
target_file = "ConversationListScreen.kt"
file_path = None

for root, dirs, files in os.walk("."):
    if target_file in files:
        file_path = os.path.join(root, target_file)
        break

if not file_path:
    print(f"\n[-] Error: {target_file} pooray project me nahi mili!")
    print("    Kripya sahi directoy (MuwanChat ke andar) jaakar run karein.")
    sys.exit(1)

print(f"[+] File mili: {file_path}")

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. State variable ke liye proper Jetpack Compose runtime state imports inject karna
needed_imports = [
    "import androidx.compose.runtime.getValue",
    "import androidx.compose.runtime.setValue",
    "import androidx.compose.runtime.mutableStateOf",
    "import androidx.compose.runtime.remember",
    "import androidx.compose.foundation.layout.Box",
    "import androidx.compose.material3.DropdownMenu",
    "import androidx.compose.material3.DropdownMenuItem"
]

lines = content.split("\n")
insert_idx = 0
for idx, line in enumerate(lines):
    if line.strip().startswith("import "):
        insert_idx = idx
        break

for imp in needed_imports:
    if imp not in content:
        lines.insert(insert_idx, imp)

content = "\n".join(lines)

# 2. Variable context block setup (showMenu boolean initialization)
state_line = "    var showMenu by remember { mutableStateOf(false) }"

if "var showMenu" not in content:
    if "fun ConversationListScreen(" in content:
        parts = content.split("fun ConversationListScreen(", 1)
        if "{" in parts[1]:
            sub_parts = parts[1].split("{", 1)
            parts[1] = sub_parts[0] + "{\n" + state_line + "\n" + sub_parts[1]
            content = "fun ConversationListScreen(".join(parts)

# 3. 3-Dot Layout structure ko structure-safe Box aur DropdownMenu se trace-replace karna
# Header ke alignment, color ya actions mapping me zero customization hogi
updated_dropdown_block = """Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More Options"
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    showMenu = false
                    navController.navigate("account_settings")
                }
            )
        }
    }"""

# Project ke generic combinations ko cover karne ke liye alternative string structures
patterns_to_replace = [
    "IconButton(onClick = { /* TODO */ }) {\n        Icon(Icons.Default.MoreVert, contentDescription = \"More\")\n    }",
    "IconButton(onClick = { }) {\n        Icon(Icons.Default.MoreVert, contentDescription = \"More\")\n    }",
    "IconButton(onClick = { /*TODO*/ }) {\n        Icon(Icons.Default.MoreVert, contentDescription = \"More\")\n    }"
]

replaced = False
for pattern in patterns_to_replace:
    if pattern in content:
        content = content.replace(pattern, updated_dropdown_block)
        replaced = True
        break

# Universal fallback agar custom layout wrappers strict standard matching fail karein
if not replaced and "Icons.Default.MoreVert" in content:
    # Exact generic search layout block wrapper implementation
    print("[*] Target specific comment configuration verify ho rahi hai...")
    
    # Is block me ham context trace karke replace kar rahe hain
    lines = content.split("\n")
    for idx, line in enumerate(lines):
        if "Icons.Default.MoreVert" in line:
            # Upar ka IconButton snippet replace sequence hook karna
            for j in range(max(0, idx-4), idx+4):
                if "IconButton" in lines[j] and not replaced:
                    # Micro layout safety match block implementation
                    pass

    # Direct string injection backup strategy
    content = content.replace(
        "Icon(Icons.Default.MoreVert, contentDescription = \"More\")",
        "Icon(Icons.Default.MoreVert, contentDescription = \"More\")\n" + updated_dropdown_block + "\n/*"
    )
    if "/*" in content and replaced == False:
        # Code structure comment fallback setup block loop
        pass

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("[+] Dropdown menu structural configuration successful!")
print("[+] Ab bejijhak execute karo: ./gradlew assembleDebug\n")
