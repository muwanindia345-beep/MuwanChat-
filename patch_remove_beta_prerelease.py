import os

def create(path, content, label):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[OK] {label}")

# ============================================================================
# build.yml -- "Create GitHub Pre-release" step hata di. Ye step signed
# assembleBetaRelease APK se ek naya GitHub Release/tag banata tha. Signing
# config experiments ki wajah se jab bhi ye step fail hota tha, poora job
# fail dikhta tha aur "Upload APK artifact" step tak nahi pahuchta tha --
# isliye artifact bhi nahi ban paata tha, jabki sirf release-creation step
# hi actually problem thi. Ab sirf artifact upload rahega, koi Release/tag
# nahi banega.
# ============================================================================
create(
    ".github/workflows/build.yml",
'''name: MuwanChat Beta Build

on:
  push:
    branches: [ main ]

jobs:
  build-beta:
    if: "!contains(github.event.head_commit.message, '[skip ci]')"
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: 8.7

      - name: Make gradlew executable
        run: chmod +x gradlew

      - name: Compute next version
        id: ver
        run: |
          CURRENT_CODE=$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')
          NEW_CODE=$((CURRENT_CODE + 1))
          NEW_NAME="2.$((NEW_CODE - 1)).0"
          echo "new_code=$NEW_CODE" >> "$GITHUB_OUTPUT"
          echo "new_name=$NEW_NAME" >> "$GITHUB_OUTPUT"
          echo "Bumping versionCode $CURRENT_CODE -> $NEW_CODE, versionName -> $NEW_NAME"

      - name: Update build.gradle.kts
        run: |
          sed -i "s/versionCode = [0-9]*/versionCode = ${{ steps.ver.outputs.new_code }}/" app/build.gradle.kts
          sed -i "s/versionName = \\".*\\"/versionName = \\"${{ steps.ver.outputs.new_name }}\\"/" app/build.gradle.kts
          grep -E "versionCode|versionName" app/build.gradle.kts

      - name: Commit version bump
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add app/build.gradle.kts
          git commit -m "chore: bump beta version to ${{ steps.ver.outputs.new_name }}-beta [skip ci]"
          git push

      - name: Create google-services.json
        run: echo '${{ secrets.GOOGLE_SERVICES_JSON }}' > app/google-services.json

      - name: Build Beta APK
        # Pehle assembleBetaDebug tha -- debug build me R8/ProGuard minification
        # hoti hi nahi, isliye beta pe sab theek chalta tha lekin official
        # (production release, minified) me WebRTC jaisi native/reflection-heavy
        # libraries crash kar jaati thi. Ab beta bhi assembleBetaRelease banata
        # hai -- same "release" buildType, same proguard-rules.pro, same
        # minifyEnabled/shrinkResources jo production use karta hai. Sirf
        # applicationId (.beta) aur signing key (beta.keystore) alag hain.
        # Isse ProGuard-wale bugs beta testing me hi pakde jaayenge.
        run: ./gradlew assembleBetaRelease

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: MuwanChat-beta
          path: app/build/outputs/apk/beta/release/app-beta-release.apk
''',
    "build.yml (Pre-release step removed, sirf artifact upload)"
)
