import re

gradle_path = "app/build.gradle.kts"
proguard_path = "app/proguard-rules.pro"

# ============================================================================
# TEST 1 -- Sirf CODE SHRINKING/OBFUSCATION (minify) ON karo, RESOURCE
# SHRINKING abhi bhi OFF rakho. Isse pata chalega crash minify se hai ya
# resource shrinking se (dono ko ek saath test karne se pehli baar confuse
# hua tha). Isko wapas off karne ke liye patch_disable_proguard_test.py
# dobara chalao.
# ============================================================================

with open(gradle_path, "r", encoding="utf-8") as f:
    gradle_content = f.read()

old_release = '''        release {
            isMinifyEnabled = false   // TEMP test
            isShrinkResources = false   // TEMP test
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")'''

new_release = '''        release {
            isMinifyEnabled = true    // TEST 1 -- code shrinking/obfuscation ON
            isShrinkResources = false // TEST 1 -- resource shrinking still OFF
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")'''

if "isMinifyEnabled = true" in gradle_content and "isShrinkResources = false" in gradle_content:
    print("[SKIP] Already in TEST 1 state (minify=true, shrinkResources=false)")
elif old_release in gradle_content:
    gradle_content = gradle_content.replace(old_release, new_release, 1)
    with open(gradle_path, "w", encoding="utf-8") as f:
        f.write(gradle_content)
    print("[OK] release block set to TEST 1: isMinifyEnabled=true, isShrinkResources=false")
else:
    print("[MANUAL FIX NEEDED] release buildType block expected shape se match nahi hua")
    print("Khud isMinifyEnabled=true aur isShrinkResources=false set karo app/build.gradle.kts me")

# ----------------------------------------------------------------------------
# Missing native-methods keep rule add karo (agar pehle se nahi hai)
# ----------------------------------------------------------------------------

with open(proguard_path, "r", encoding="utf-8") as f:
    proguard_content = f.read()

native_rule_marker = "native <methods>;"

if native_rule_marker in proguard_content:
    print("[SKIP] Native-methods keep rule already present in proguard-rules.pro")
else:
    anchor = "-keep class org.webrtc.** { *; }\n-keepclassmembers class org.webrtc.** { *; }\n-dontwarn org.webrtc.**"
    addition = anchor + '''

# JNI native method registration ke liye -- kisi bhi class me agar native
# method hai, uska naam/signature R8 se exact rehna chahiye, warna
# RegisterNatives() fail hoke native side SIGTRAP jaisa crash de sakta hai.
-keepclasseswithmembernames class * {
    native <methods>;
}'''
    if anchor in proguard_content:
        proguard_content = proguard_content.replace(anchor, addition, 1)
        with open(proguard_path, "w", encoding="utf-8") as f:
            f.write(proguard_content)
        print("[OK] Native-methods keep rule added after the WebRTC section")
    else:
        # Fallback: just append at the end of the file
        proguard_content = proguard_content.rstrip("\n") + "\n\n" + addition + "\n"
        with open(proguard_path, "w", encoding="utf-8") as f:
            f.write(proguard_content)
        print("[OK] WebRTC anchor exact match nahi mila -- native-methods rule file ke end me append kar diya")

print("\nAgla step: `./gradlew clean` chalao, phir release build banao aur call test karo.")
