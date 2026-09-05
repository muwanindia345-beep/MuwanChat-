import re

path = "app/build.gradle.kts"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# ============================================================================
# TEMPORARY TEST -- ProGuard/R8 minification band kar rahe hain taaki confirm
# ho sake ki SIGTRAP crash ka ProGuard se koi lena dena hai ya nahi.
# Isko wapas laane ke liye patch_reenable_proguard.py chalao.
# ============================================================================

old = '''        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }'''

new = '''        release {
            isMinifyEnabled = false   // TEMPORARY -- crash test ke liye off
            isShrinkResources = false // TEMPORARY -- crash test ke liye off
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }'''

if "isMinifyEnabled = false" in content:
    print("[SKIP] ProGuard already off hai")
elif old in content:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("[OK] ProGuard/R8 temporarily OFF kar diya (dono flavors ke liye -- beta aur production dono 'release' buildType share karte hain)")
else:
    print("[MANUAL FIX NEEDED] release buildType ka block expected shape se match nahi hua -- khud isMinifyEnabled/isShrinkResources ko false karo.")
