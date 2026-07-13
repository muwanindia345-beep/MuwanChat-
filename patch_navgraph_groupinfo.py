import io
path = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old1 = """    object GroupChat       : Screen("group_chat/{groupId}/{groupName}") {
        fun createRoute(groupId: String, groupName: String) =
            "group_chat/$groupId/${android.net.Uri.encode(groupName)}"
    }
}"""
new1 = """    object GroupChat       : Screen("group_chat/{groupId}/{groupName}") {
        fun createRoute(groupId: String, groupName: String) =
            "group_chat/$groupId/${android.net.Uri.encode(groupName)}"
    }
    object GroupInfo       : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
}"""

old2 = """        composable(Screen.GroupChat.route) { back ->
            GroupChatScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: "",
                groupName = back.arguments?.getString("groupName") ?: "New Group",
                groupAvatar = null
            )
        }
    }
}"""
new2 = """        composable(Screen.GroupChat.route) { back ->
            GroupChatScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: "",
                groupName = back.arguments?.getString("groupName") ?: "New Group",
                groupAvatar = null
            )
        }
        composable(Screen.GroupInfo.route) { back ->
            GroupInfoScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
    }
}"""

for old, new, label in [(old1, new1, "Screen sealed class"), (old2, new2, "NavHost composable")]:
    c = content.count(old)
    if c != 1:
        print(f"MATCH FAILED for {label}: found {c}, expected 1")
    else:
        content = content.replace(old, new)

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched NavGraph.kt")
