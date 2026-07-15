#!/usr/bin/env python3
import pathlib, sys

TARGET = pathlib.Path("app/build.gradle.kts")
OLD = '    implementation("androidx.core:core-ktx:1.12.0")'
NEW = (
    '    implementation("androidx.core:core-ktx:1.12.0")\n'
    '    implementation("androidx.core:core-splashscreen:1.0.1")'
)

def main():
    if not TARGET.exists():
        print(f"ERROR: {TARGET} not found. Run from repo root."); sys.exit(1)
    text = TARGET.read_text(encoding="utf-8")
    if "core-splashscreen" in text:
        print("Already patched, nothing to do."); return
    if OLD not in text:
        print("ERROR: expected line not found."); sys.exit(1)
    text = text.replace(OLD, NEW)
    TARGET.write_text(text, encoding="utf-8")
    print(f"Patched: {TARGET}")

if __name__ == "__main__":
    main()
