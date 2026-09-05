import re

path = "app/build.gradle.kts"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# ============================================================================
# Beta flavor abhi "release" (production) signingConfig use kar raha tha ya
# koi signingConfig assign hi nahi tha -- isliye CI mein
# "SigningConfig 'release' is missing required property 'storeFile'" error
# aa raha tha (KEYSTORE_PATH env var sirf official release workflow mein
# set hota hai, beta workflow mein nahi). Beta ko apni fixed committed
# keystore (betaRelease) use karni chahiye.
# ============================================================================

old_block = '''        create("beta") {
            dimension = "channel"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "TalkWave Beta")
            buildConfigField("boolean", "ENABLE_NEW_NAV", "true")
        }'''

new_block = '''        create("beta") {
            dimension = "channel"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "TalkWave Beta")
            buildConfigField("boolean", "ENABLE_NEW_NAV", "true")
            signingConfig = signingConfigs.getByName("betaRelease")
        }'''

if "signingConfig = signingConfigs.getByName(\"betaRelease\")" in content:
    print("[SKIP] beta flavor already has signingConfig = betaRelease -- kuch nahi badla")
elif old_block in content:
    content = content.replace(old_block, new_block, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("[OK] beta flavor mein signingConfig = betaRelease add kar diya")
else:
    print("[MANUAL FIX NEEDED] beta flavor ka block expected shape se match nahi hua.")
    print("Khud app/build.gradle.kts mein productFlavors { create(\"beta\") { ... } } ke")
    print("andar ye line add karo:")
    print('    signingConfig = signingConfigs.getByName("betaRelease")')
