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

# ---------------------------------------------------------------------------
# Gradle 8.2 (jo pichli patch mein pin kiya tha) AGP 8.5.2 ke liye bhi bahut
# purana nikla -- AGP khud bol raha hai minimum 8.7 chahiye. Ab 8.7 pe bump.
# gradle-wrapper.properties (source of truth, Termux local build ke liye
# bhi) + dono CI workflow pins, teeno jagah consistent rakha.
# ---------------------------------------------------------------------------
apply(
    "gradle/wrapper/gradle-wrapper.properties",
'''distributionUrl=https\\://services.gradle.org/distributions/gradle-8.2-bin.zip''',
'''distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip''',
    "gradle-wrapper.properties: bump to 8.7"
)

for path in [".github/workflows/build.yml", ".github/workflows/release.yml"]:
    apply(
        path,
'''          gradle-version: 8.2''',
'''          gradle-version: 8.7''',
        f"{path}: bump CI Gradle pin to 8.7"
    )

print("\n[DONE] Gradle bumped 8.2 -> 8.7 across wrapper properties + both CI workflows")
