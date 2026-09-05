import re

gradle_path = "app/build.gradle.kts"

# ============================================================================
# TEST 1 ko revert karo -- wapas dono OFF (minify=false, shrinkResources=false)
# kyunki crash reproduce ho gaya minify=true, shrinkResources=false state me.
# Isse wapas TEST 1 pe jaane ke liye patch_test_minify_only.py chalao.
# ============================================================================

with open(gradle_path, "r", encoding="utf-8") as f:
    content = f.read()

old_release = '''        release {
            isMinifyEnabled = true    // TEST 1 -- code shrinking/obfuscation ON
            isShrinkResources = false // TEST 1 -- resource shrinking still OFF
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")'''

new_release = '''        release {
            isMinifyEnabled = false   // TEMP test
            isShrinkResources = false   // TEMP test
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")'''

if "isMinifyEnabled = false" in content and "isShrinkResources = false" in content:
    print("[SKIP] ProGuard already off hai (minify=false, shrinkResources=false)")
elif old_release in content:
    content = content.replace(old_release, new_release, 1)
    with open(gradle_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("[OK] ProGuard/R8 wapas OFF kar diya (minify=false, shrinkResources=false)")
else:
    print("[MANUAL FIX NEEDED] release buildType block expected shape se match nahi hua")
    print("Khud isMinifyEnabled=false aur isShrinkResources=false set karo app/build.gradle.kts me")
