#!/data/data/com.termux/files/usr/bin/bash
# patch_settings_profile_move.sh
# Moves the Profile shortcut from ConversationList top bar into SettingsScreen
# (between Accepted Users and Check Updates).
#
# Run from the repo root (MuwanChat--main/):
#   bash patch_settings_profile_move.sh

set -e

CONV_FILE="app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
SETTINGS_FILE="app/src/main/java/com/muwan/muwanchat/screens/SettingsScreen.kt"

if [ ! -f "$CONV_FILE" ] || [ ! -f "$SETTINGS_FILE" ]; then
  echo "Error: run this script from the MuwanChat--main repo root."
  exit 1
fi

python3 - "$CONV_FILE" "$SETTINGS_FILE" << 'PYEOF'
import sys

conv_path, settings_path = sys.argv[1], sys.argv[2]

# --- 1. ConversationListScreen.kt: remove Profile IconButton from top bar ---
with open(conv_path, "r", encoding="utf-8") as f:
    conv = f.read()

old_conv = """                    Text("MuwanChat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Row {
                        IconButton(onClick = {
                            navController.navigate(Screen.Profile.createRoute("edit"))
                        }) {
                            Icon(Icons.Filled.Person, contentDescription = "Profile", tint = Color.White)
                        }
                        BadgedBox(badge = {"""

new_conv = """                    Text("MuwanChat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Row {
                        BadgedBox(badge = {"""

if old_conv not in conv:
    print(f"SKIP: expected block not found in {conv_path} (already patched?)")
else:
    conv = conv.replace(old_conv, new_conv, 1)
    with open(conv_path, "w", encoding="utf-8") as f:
        f.write(conv)
    print(f"OK: patched {conv_path}")

# --- 2. SettingsScreen.kt: add Profile row before "Check Updates" ---
with open(settings_path, "r", encoding="utf-8") as f:
    settings = f.read()

anchor = """        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 3.5 Check Updates"""

insertion = """        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 3.4 Profile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Screen.Profile.createRoute("edit")) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Profile", tint = Color.White)
            Spacer(modifier = Modifier.width(16.dp))
            Text("Profile", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 3.5 Check Updates"""

if "// 3.4 Profile" in settings:
    print(f"SKIP: {settings_path} already patched")
elif anchor not in settings:
    print(f"ERROR: anchor not found in {settings_path}")
else:
    settings = settings.replace(anchor, insertion, 1)
    with open(settings_path, "w", encoding="utf-8") as f:
        f.write(settings)
    print(f"OK: patched {settings_path}")
PYEOF

echo "Done."
