#!/usr/bin/env python3
import pathlib, sys

TARGET = pathlib.Path("app/src/main/AndroidManifest.xml")

OLD = """        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">"""

NEW = """        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.MuwanChat.Splash"
            android:windowSoftInputMode="adjustResize">"""

def main():
    if not TARGET.exists():
        print(f"ERROR: {TARGET} not found. Run from repo root."); sys.exit(1)
    text = TARGET.read_text(encoding="utf-8")
    if 'android:theme="@style/Theme.MuwanChat.Splash"' in text:
        print("Already patched, nothing to do."); return
    if OLD not in text:
        print("ERROR: expected <activity> block not found."); sys.exit(1)
    text = text.replace(OLD, NEW)
    TARGET.write_text(text, encoding="utf-8")
    print(f"Patched: {TARGET}")

if __name__ == "__main__":
    main()
