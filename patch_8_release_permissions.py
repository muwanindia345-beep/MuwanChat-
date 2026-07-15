#!/usr/bin/env python3
import pathlib, sys

TARGET = pathlib.Path(".github/workflows/release.yml")

OLD = """jobs:
  build-release:
    runs-on: ubuntu-latest

    steps:"""

NEW = """jobs:
  build-release:
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:"""

def main():
    if not TARGET.exists():
        print(f"ERROR: {TARGET} not found. Run from repo root.")
        sys.exit(1)
    text = TARGET.read_text(encoding="utf-8")
    if "permissions:" in text:
        print("Already patched, nothing to do.")
        return
    if OLD not in text:
        print("ERROR: expected block not found — file may have changed.")
        sys.exit(1)
    text = text.replace(OLD, NEW)
    TARGET.write_text(text, encoding="utf-8")
    print(f"Patched: {TARGET}")

if __name__ == "__main__":
    main()
