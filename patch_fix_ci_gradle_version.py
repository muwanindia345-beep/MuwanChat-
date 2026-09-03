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
# Root cause: repo ka gradlew ek stub hai ("exec gradle \"$@\"") jo seedha
# system-installed Gradle use karta hai, gradle-wrapper.properties mein
# pinned 8.2 ko ignore karke. GitHub Actions runner pe latest Gradle (9.7.1)
# mil raha hai jo purane Kotlin plugin (1.9.22) ko resolve nahi kar paata.
# Fix: setup-gradle action ko explicitly 8.2 pin karo dono workflows mein.
# ---------------------------------------------------------------------------
for path in [".github/workflows/build.yml", ".github/workflows/release.yml"]:
    apply(
        path,
'''      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
''',
'''      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: 8.2
''',
        f"{path}: pin Gradle to 8.2"
    )

print("\n[DONE] CI Gradle version pinned to 8.2 in both workflows")
