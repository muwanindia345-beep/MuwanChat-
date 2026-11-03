import sys, os

# Usage: python landing_version_patch.py <repo_root>
# When the app opens the landing page from the Update button, it appends the installed
# version to the link, e.g. .../talkwave-download/?v=2.307.0&c=308
# The landing page reads these values to show "Update" or "Up to date" instead of "Download APK".

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
PATH = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat/data/UpdateManager.kt")

s = open(PATH, encoding="utf-8").read()
if "LANDING_VERSION_V1" in s:
    print("Already patched, nothing changed.")
    sys.exit(0)

old = '''        val url = BuildConfig.UPDATE_PAGE_URL
        if (url.isBlank()) return
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}'''

new = '''        val url = BuildConfig.UPDATE_PAGE_URL
        if (url.isBlank()) return
        try {
            // LANDING_VERSION_V1: tell the landing page which version is installed
            val uri = Uri.parse(url).buildUpon()
                .appendQueryParameter("v", BuildConfig.VERSION_NAME)
                .appendQueryParameter("c", BuildConfig.VERSION_CODE.toString())
                .build()
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}'''

n = s.count(old)
if n != 1:
    print("FAIL UpdateManager.kt: matched %d times" % n)
    sys.exit(1)
open(PATH, "w", encoding="utf-8").write(s.replace(old, new))
print("OK: UpdateManager.openUpdatePage now appends ?v=<versionName>&c=<versionCode>")
