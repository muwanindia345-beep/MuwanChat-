import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/UserProfileScreen.kt"
content = open(path, encoding="utf-8").read()

# --- Edit 1: toEntity signature ---
old1 = "private fun UserItem.toEntity() = CachedUserProfileEntity(\n    uid = uid, username = username, name = name, bio = bio,\n    city = city, country = country, gender = gender, avatar = avatar\n)"
new1 = "private fun UserItem.toEntity(status: String) = CachedUserProfileEntity(\n    uid = uid, username = username, name = name, bio = bio,\n    city = city, country = country, gender = gender, avatar = avatar, status = status\n)"
if old1 not in content:
    sys.exit("Anchor 1 not found -- yahan bhi kuch farak hai, mujhe 30-45 line ka sed output bhejo.")
content = content.replace(old1, new1, 1)

# --- Edit 2: cache-read effect ---
old2 = 'val cached = db.cachedUserProfileDao().get(uid)\n        if (cached != null) {\n            user = cached.toModel()\n            isLoading = false\n        }'
new2 = 'val cached = db.cachedUserProfileDao().get(uid)\n        if (cached != null) {\n            user = cached.toModel()\n            status = cached.status\n            isLoading = false\n        }'
if old2 not in content:
    sys.exit("Anchor 2 not found -- 55-65 line ka sed output bhejo.")
content = content.replace(old2, new2, 1)

# --- Edit 3: background refresh writes status into cache ---
old3 = '''val userRes = RetrofitClient.usersApi.getUserByUid("Bearer $token", uid)
            if (userRes.isSuccessful) {
                val fresh = userRes.body()?.user
                if (fresh != null) {
                    user = fresh
                    db.cachedUserProfileDao().upsert(fresh.toEntity())
                }
            } else if (user == null) {
                errorMsg = "User not found"
            }

            val statusRes = RetrofitClient.usersApi.getStatuses("Bearer $token", uid)
            if (statusRes.isSuccessful) status = statusRes.body()?.statuses?.get(uid) ?: "none"'''

new3 = '''val userRes = RetrofitClient.usersApi.getUserByUid("Bearer $token", uid)
            var freshUser: UserItem? = null
            if (userRes.isSuccessful) {
                freshUser = userRes.body()?.user
                if (freshUser != null) user = freshUser
            } else if (user == null) {
                errorMsg = "User not found"
            }

            val statusRes = RetrofitClient.usersApi.getStatuses("Bearer $token", uid)
            if (statusRes.isSuccessful) {
                status = statusRes.body()?.statuses?.get(uid) ?: "none"
            }

            val u = freshUser ?: user
            if (u != null) db.cachedUserProfileDao().upsert(u.toEntity(status))'''

if old3 not in content:
    sys.exit("Anchor 3 not found -- 75-90 line ka sed output bhejo.")
content = content.replace(old3, new3, 1)

open(path, "w", encoding="utf-8").write(content)
print("UserProfileScreen.kt patched: status ab offline cache hota hai.")
