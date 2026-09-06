import re

PATH = "app/build.gradle.kts"

# ============================================================================
# Bug: build-beta CI always failed with
#   "SigningConfig 'release' is missing required property 'storeFile'"
# even though the beta flavor explicitly sets signingConfig = betaRelease.
#
# Reason: signingConfig was set BOTH in buildTypes.release AND in
# productFlavors.beta. When AGP merges a flavor + buildType into a variant
# (here: beta + release -> betaRelease), the buildType's signingConfig wins
# over the flavor's -- so it silently ignored betaRelease and tried to use
# "release", whose storeFile only comes from the KEYSTORE_PATH env var
# (not set in CI).
#
# Fix: remove signingConfig from buildTypes.release, and set it explicitly
# per-flavor instead (production -> release, beta -> betaRelease already
# present). This removes the ambiguity entirely.
# ============================================================================

with open(PATH, "r", encoding="utf-8") as f:
    content = f.read()

old_build_type_block = '''            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            firebaseCrashlytics {
                nativeSymbolUploadEnabled = true
            }
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        // Official build — same applicationId as always, ships only confirmed features.
        create("production") {
            dimension = "channel"
            buildConfigField("boolean", "ENABLE_NEW_NAV", "false")
        }'''

new_build_type_block = '''            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            firebaseCrashlytics {
                nativeSymbolUploadEnabled = true
            }
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        // Official build — same applicationId as always, ships only confirmed features.
        create("production") {
            dimension = "channel"
            buildConfigField("boolean", "ENABLE_NEW_NAV", "false")
            signingConfig = signingConfigs.getByName("release")
        }'''

if old_build_type_block not in content:
    if new_build_type_block in content:
        print(f"[SKIP] {PATH} already patched")
    else:
        raise SystemExit(
            f"[FAIL] Expected block not found in {PATH} -- "
            "file may have changed, patch not applied."
        )
else:
    content = content.replace(old_build_type_block, new_build_type_block, 1)
    with open(PATH, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[OK] Fixed signingConfig override bug in {PATH}")
