import sys, os

# Usage: python telegram_patch.py <repo_root>
# Appends a "Send to Telegram" step to the end of release.yml:
# the official APK, what's new, and the landing page link are posted to the channel.
# Required secrets: TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID
# If the secrets are missing or Telegram fails, the release does NOT fail (continue-on-error).

LANDING = "https://muwanindia345-beep.github.io/talkwave-download/"

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
PATH = os.path.join(ROOT, ".github/workflows/release.yml")

s = open(PATH, encoding="utf-8").read()
if "TELEGRAM_V1" in s:
    print("Already patched, nothing changed.")
    sys.exit(0)

STEP = '''
      # TELEGRAM_V1
      - name: Send to Telegram
        continue-on-error: true
        env:
          TG_TOKEN: ${{ secrets.TELEGRAM_BOT_TOKEN }}
          TG_CHAT: ${{ secrets.TELEGRAM_CHAT_ID }}
          VERSION: ${{ steps.ver.outputs.new_name }}
          LANDING: __LANDING__
        run: |
          if [ -z "$TG_TOKEN" ] || [ -z "$TG_CHAT" ]; then
            echo "Telegram secrets are not set, skipping."
            exit 0
          fi
          APK=app/build/outputs/apk/production/release/app-production-release.apk
          NOTES_TXT=$(tail -n +4 release_notes.md)
          CAPTION=$(printf '🚀 TalkWave v%s\\n\\n%s\\n\\n🌐 Download page:\\n%s' "$VERSION" "$NOTES_TXT" "$LANDING")
          # Telegram caption limit 1024 characters
          CAPTION=$(printf '%s' "$CAPTION" | python3 -c "import sys;print(sys.stdin.read()[:950],end='')")

          if ! curl -sS --fail -X POST "https://api.telegram.org/bot${TG_TOKEN}/sendDocument" \\
              -F chat_id="$TG_CHAT" \\
              -F document=@"$APK" \\
              -F caption="$CAPTION"; then
            echo "APK upload failed (possibly larger than 50 MB), sending the message only."
            curl -sS --fail -X POST "https://api.telegram.org/bot${TG_TOKEN}/sendMessage" \\
              -F chat_id="$TG_CHAT" \\
              -F text="$CAPTION"
          fi
'''.replace("__LANDING__", LANDING)

if not s.endswith("\n"):
    s += "\n"
s += STEP
open(PATH, "w", encoding="utf-8").write(s)
print("OK: Telegram step added to release.yml")
