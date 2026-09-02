#!/data/data/com.termux/files/usr/bin/bash
set -e

THEMES="app/src/main/res/values/themes.xml"

if [ ! -f "$THEMES" ]; then
  echo "ERROR: run this from project root (where app/ folder is)"
  exit 1
fi

sed -i 's/windowSplashScreenBackground">#1a1a2e</windowSplashScreenBackground">#010113</' "$THEMES"

echo "Done: system splash background updated to #010113 (matches new polished icon bg)."
