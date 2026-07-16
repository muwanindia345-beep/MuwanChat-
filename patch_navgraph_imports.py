import sys

path = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

anchor = "import androidx.compose.runtime.Composable"
new_line = "import androidx.compose.runtime.*"

count = content.count(anchor)
if count == 0:
    print("ERROR: anchor not found — manual check karo.")
    sys.exit(1)
if count > 1:
    print(f"ERROR: anchor {count} baar mila, unique nahi hai — manual check karo.")
    sys.exit(1)

content = content.replace(anchor, new_line, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ NavGraph.kt me runtime.* import ho gaya (remember/mutableStateOf/LaunchedEffect fix).")
