#!/data/data/com.termux/files/usr/bin/bash
# ===========================================================================
# TalkWave / MuwanChat — cleanup script
# Deletes the 44 individual patch_*.py scripts (already consolidated into
# applied_patches_archive.sh on 2026-09-04) and stray .bak files left in
# screens/. Run this from the project root (where app/ folder is).
# ===========================================================================
set -e

if [ ! -d "app" ]; then
  echo "ERROR: run this from project root (where app/ folder is)"
  exit 1
fi

echo "Deleting patch_*.py scripts..."
rm -fv patch_*.py

echo ""
echo "Deleting stray .bak files in screens/..."
rm -fv app/src/main/java/com/muwan/muwanchat/screens/*.bak*

echo ""
echo "Done. Review with 'git status' before committing."
