#!/usr/bin/env python3
import pathlib, sys

TARGET = pathlib.Path("app/src/main/java/com/muwan/muwanchat/MainActivity.kt")

OLD_IMPORT = "import androidx.activity.compose.setContent"
NEW_IMPORT = (
    "import androidx.activity.compose.setContent\n"
    "import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen"
)

OLD_ONCREATE = """    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()"""

NEW_ONCREATE = """    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()"""

def main():
    if not TARGET.exists():
        print(f"ERROR: {TARGET} not found. Run from repo root."); sys.exit(1)
    text = TARGET.read_text(encoding="utf-8")
    changed = False

    if "installSplashScreen" in text:
        print("Already patched, nothing to do."); return

    if OLD_IMPORT not in text:
        print("ERROR: import line not found."); sys.exit(1)
    text = text.replace(OLD_IMPORT, NEW_IMPORT)
    changed = True

    if OLD_ONCREATE not in text:
        print("ERROR: onCreate block not found."); sys.exit(1)
    text = text.replace(OLD_ONCREATE, NEW_ONCREATE)
    changed = True

    if changed:
        TARGET.write_text(text, encoding="utf-8")
        print(f"Patched: {TARGET}")

if __name__ == "__main__":
    main()
