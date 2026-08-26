path = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Fix 1: navArgument import missing tha
old_import = "import androidx.navigation.navDeepLink"
new_import = "import androidx.navigation.navDeepLink\nimport androidx.navigation.navArgument"
if old_import not in content:
    print("import anchor missing!"); exit(1)
content = content.replace(old_import, new_import, 1)

# Fix 2: named arg (fromChat=) ke baad positional args allowed nahi Kotlin mein
old_call = '''            UserProfileScreen(
                fromChat = fromChat,navController, back.arguments?.getString("uid") ?: "")'''
new_call = '''            UserProfileScreen(
                navController = navController,
                uid = back.arguments?.getString("uid") ?: "",
                fromChat = fromChat
            )'''
if old_call not in content:
    print("call anchor missing!"); exit(1)
content = content.replace(old_call, new_call, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("NavGraph.kt: navArgument import fixed + UserProfileScreen call argument order fixed")
