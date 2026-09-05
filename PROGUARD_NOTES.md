# ⚠️ ProGuard/R8 is ACTIVE — read this before adding any new library or feature

Both **beta** (`assembleBetaRelease`) and **official** (`assembleProductionRelease`)
builds now go through full R8 minification + obfuscation + resource shrinking
(`isMinifyEnabled = true`, `isShrinkResources = true` in `app/build.gradle.kts`,
rules in `app/proguard-rules.pro`). There is no more "unminified beta" — what
you test on beta is now built the exact same way as what ships officially.

## Why this file exists

CallScreen (WebRTC-based voice/video calling) crashed only in the official
release APK, not in beta — because beta used to build as `assembleBetaDebug`
(no minification at all). R8 had silently stripped/renamed `org.webrtc.**`
classes in the release build since nothing in `proguard-rules.pro` told it
those classes were needed. WebRTC's native (JNI/C++) layer looks up Java
classes and methods by their **exact original name** — it has no idea R8
renamed them — so at runtime the native side couldn't find what it needed
and the app crashed the moment a call was placed.

This is a general class of bug, not a one-off. It will happen again with any
future library that:
- Uses **JNI / native code** (calls into Java/Kotlin classes by string name
  from C/C++ — WebRTC, ML/AI SDKs, some camera or audio libraries)
- Uses **reflection** on classes it didn't compile itself (Gson without
  `@SerializedName`, Room entities, generic `TypeToken`s, Retrofit interfaces)
- Has callback/observer interfaces implemented by app code that are only
  ever invoked from outside normal Kotlin call chains (SDL callbacks, native
  observer patterns like WebRTC's `SdpObserver` / `PeerConnection.Observer`)

R8's static analysis can't see any of the above as "used," so by default it
is fair game to rename or delete — unless a `-keep` rule says otherwise.

## What to do before adding any new dependency

1. **Ask: does this library touch JNI, reflection, or serialization?**
   If unsure, assume yes for anything that isn't pure Kotlin/Compose UI code.
2. **Check the library's own docs/GitHub repo for a "ProGuard" or "R8"
   section.** Most well-maintained libraries (WebRTC, Firebase, Room,
   Retrofit, Gson) publish their own recommended `-keep` rules — copy them
   into `app/proguard-rules.pro` under a clearly labeled section, matching
   the style already used there (see the WebRTC section as an example).
3. **Add the rule at the same time you add the feature**, not after
   something crashes in production. Don't wait for a bug report.
4. **Test on a release build before considering the feature done.** Beta is
   now release-type, so a normal beta build IS this test — but if you're
   iterating locally, remember `assembleBetaDebug` / `assembleDebug` will
   NOT catch these bugs since local debug builds skip minification.
5. If a crash only reproduces in a release/minified build and not in a
   plain debug build, **ProGuard stripping a class/method is the first
   suspect** — check `app/build/outputs/mapping/*/mapping.txt` for the
   affected class if you need to confirm what got renamed.

## Where the rules live

`app/proguard-rules.pro` — organized by library, each section commented
with *why* the rule exists (not just what it does). Keep that pattern going.
