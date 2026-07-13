import io
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old_import = """import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkInputBg"""
new_import = """import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkInputBg
import com.muwan.muwanchat.navigation.Screen"""

old_tap = """                onHeaderTap = {
                    // TEMPORARY: GroupInfoScreen abhi nahi bana — jab tak wo nahi banta
                    // tab tak Coming Soon dikhayenge taaki navigate() crash na ho
                    // (route registered nahi hai). GroupInfoScreen bante hi is line ko
                    // navController.navigate(Screen.GroupInfo.createRoute(groupId)) se replace karna hai.
                    comingSoonFeature = "👥 Group Info"
                },"""
new_tap = """                onHeaderTap = {
                    navController.navigate(Screen.GroupInfo.createRoute(groupId))
                },"""

for old, new, label in [(old_import, new_import, "import"), (old_tap, new_tap, "header tap")]:
    c = content.count(old)
    if c != 1:
        print(f"MATCH FAILED for {label}: found {c}, expected 1")
    else:
        content = content.replace(old, new)

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched GroupChatScreen.kt")
