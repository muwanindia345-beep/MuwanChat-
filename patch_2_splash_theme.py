#!/usr/bin/env python3
import pathlib, sys

TARGET = pathlib.Path("app/src/main/res/values/themes.xml")

OLD = """<resources>
    <style name="Theme.MuwanChat" parent="android:Theme.DeviceDefault.NoActionBar">
        <item name="android:windowBackground">#1a1a2e</item>
        <item name="android:statusBarColor">#1a1a2e</item>
        <item name="android:navigationBarColor">#1a1a2e</item>
    </style>
</resources>"""

NEW = """<resources>
    <style name="Theme.MuwanChat" parent="android:Theme.DeviceDefault.NoActionBar">
        <item name="android:windowBackground">#1a1a2e</item>
        <item name="android:statusBarColor">#1a1a2e</item>
        <item name="android:navigationBarColor">#1a1a2e</item>
    </style>

    <style name="Theme.MuwanChat.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#1a1a2e</item>
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>
        <item name="postSplashScreenTheme">@style/Theme.MuwanChat</item>
    </style>
</resources>"""

def main():
    if not TARGET.exists():
        print(f"ERROR: {TARGET} not found. Run from repo root."); sys.exit(1)
    text = TARGET.read_text(encoding="utf-8")
    if "Theme.MuwanChat.Splash" in text:
        print("Already patched, nothing to do."); return
    if OLD not in text:
        print("ERROR: expected content not found — file may have changed."); sys.exit(1)
    text = text.replace(OLD, NEW)
    TARGET.write_text(text, encoding="utf-8")
    print(f"Patched: {TARGET}")

if __name__ == "__main__":
    main()
