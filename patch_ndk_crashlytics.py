def apply(path, old, new, label):
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    n = src.count(old)
    if n != 1:
        raise SystemExit(f"[FAIL] {label} ({path}): found {n} matches (expected 1)")
    src = src.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print(f"[OK] {label}")

apply(
    "app/build.gradle.kts",
'''    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }''',
'''    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            firebaseCrashlytics {
                nativeSymbolUploadEnabled = true
            }
        }
    }''',
    "build.gradle.kts: enable native symbol upload for release"
)

apply(
    "app/build.gradle.kts",
'''    implementation("com.google.firebase:firebase-crashlytics-ktx")''',
'''    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ndk")''',
    "build.gradle.kts: add firebase-crashlytics-ndk dependency"
)

print("\n[DONE] NDK crash reporting enabled")
