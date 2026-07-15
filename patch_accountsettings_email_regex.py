#!/usr/bin/env python3
"""
Fix build error:
  e: AccountSettingsScreen.kt:49:9 Unresolved reference: !

Cause: EMAIL_REGEX.matchEntire(e.trim()) returns MatchResult? (nullable),
not Boolean, so '!' can't be applied to it directly.

Run from repo root:
  python3 patch_accountsettings_email_regex.py
"""

import pathlib
import sys

TARGET = pathlib.Path("app/src/main/java/com/muwan/muwanchat/screens/AccountSettingsScreen.kt")

OLD = '    if (!EMAIL_REGEX.matchEntire(e.trim())) return "Invalid email format"'
NEW = '    if (EMAIL_REGEX.matchEntire(e.trim()) == null) return "Invalid email format"'

def main():
    if not TARGET.exists():
        print(f"ERROR: {TARGET} not found. Run this from the repo root.")
        sys.exit(1)

    text = TARGET.read_text(encoding="utf-8")

    if NEW in text:
        print("Already patched, nothing to do.")
        return

    if OLD not in text:
        print("ERROR: expected old line not found. File may have changed.")
        print("Looking for:")
        print(OLD)
        sys.exit(1)

    text = text.replace(OLD, NEW)
    TARGET.write_text(text, encoding="utf-8")
    print(f"Patched: {TARGET}")

if __name__ == "__main__":
    main()
