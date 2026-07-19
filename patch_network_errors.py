BASE = "app/src/main/java/com/muwan/muwanchat/screens"
UTIL_IMPORT = "import com.muwan.muwanchat.util.friendlyErrorMessage\n"

def patch_file(fname, import_anchor, replacements):
    path = f"{BASE}/{fname}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    changes = 0

    if UTIL_IMPORT not in content:
        if import_anchor in content:
            content = content.replace(import_anchor, import_anchor + UTIL_IMPORT, 1)
            changes += 1
        else:
            print(f"WARN [{fname}]: import anchor not found")

    for old, new in replacements:
        count = content.count(old)
        if count == 0:
            print(f"WARN [{fname}]: pattern not found -> {old!r}")
        else:
            content = content.replace(old, new)
            changes += count

    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"{fname}: {changes} changes")

patch_file(
    "AcceptedUsersScreen.kt",
    "import com.muwan.muwanchat.network.RetrofitClient\n",
    [('errorMsg = e.message ?: "Network error"', 'errorMsg = friendlyErrorMessage(e)')]
)

patch_file(
    "AddFromContactsScreen.kt",
    "import com.muwan.muwanchat.network.RetrofitClient\n",
    [('errorMsg = e.message ?: "Error"', 'errorMsg = friendlyErrorMessage(e)')]
)

patch_file(
    "ApprovalRequestsScreen.kt",
    "import com.muwan.muwanchat.network.RetrofitClient\n",
    [('errorMsg = e.message ?: "Network error"', 'errorMsg = friendlyErrorMessage(e)')]
)

patch_file(
    "LoginScreen.kt",
    "import com.muwan.muwanchat.network.RetrofitClient\n",
    [('errorMsg = "Error: ${e.message}"', 'errorMsg = friendlyErrorMessage(e)')]
)

patch_file(
    "PhoneOTPScreen.kt",
    "import com.muwan.muwanchat.network.RetrofitClient\n",
    [('errorMsg = "Network error: ${e.message}"', 'errorMsg = friendlyErrorMessage(e)')]
)

patch_file(
    "RegisterScreen.kt",
    "import com.muwan.muwanchat.network.RetrofitClient\n",
    [('errorMsg = "Network error: ${e.message}"', 'errorMsg = friendlyErrorMessage(e)')]
)

patch_file(
    "SearchMembersForGroupScreen.kt",
    "import com.muwan.muwanchat.network.RetrofitClient\n",
    [('errorMsg = e.message ?: "Error"', 'errorMsg = friendlyErrorMessage(e)')]
)

patch_file(
    "UserProfileScreen.kt",
    "import com.muwan.muwanchat.network.RetrofitClient\n",
    [
        ('errorMsg = e.message ?: "Network error"', 'errorMsg = friendlyErrorMessage(e)'),
        ('errorMsg = e.message ?: "Error"', 'errorMsg = friendlyErrorMessage(e)'),
    ]
)

print("Done.")
