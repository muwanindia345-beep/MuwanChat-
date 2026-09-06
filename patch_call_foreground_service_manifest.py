# -*- coding: utf-8 -*-
# CallForegroundService AndroidManifest.xml me register hi nahi tha, isliye
# incoming-call notification (Accept/Decline) kabhi dikhi hi nahi. Yeh patch
# service ko declare karta hai + zaruri foreground-service permissions add
# karta hai (Android 14 / API 34+ pe in ke bina crash ya silent-fail hota hai).

MANIFEST = "app/src/main/AndroidManifest.xml"

with open(MANIFEST, "r", encoding="utf-8") as f:
    content = f.read()

# 1) Naye permissions add karo (existing CAMERA permission ke baad)
old_perms = '    <uses-permission android:name="android.permission.CAMERA" />\n'
new_perms = (
    '    <uses-permission android:name="android.permission.CAMERA" />\n'
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n'
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />\n'
    '    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />\n'
)
if old_perms not in content:
    raise SystemExit("[FAIL] CAMERA permission line not found -- manifest changed, patch by hand")
content = content.replace(old_perms, new_perms, 1)

# 2) CallForegroundService ko declare karo, MuwanFirebaseService <service> ke just pehle
old_service = (
    '        <service\n'
    '            android:name=".MuwanFirebaseService"\n'
)
new_service = (
    '        <service\n'
    '            android:name=".calling.CallForegroundService"\n'
    '            android:exported="false"\n'
    '            android:foregroundServiceType="phoneCall" />\n'
    '\n'
    '        <service\n'
    '            android:name=".MuwanFirebaseService"\n'
)
if old_service not in content:
    raise SystemExit("[FAIL] MuwanFirebaseService <service> block not found -- manifest changed, patch by hand")
content = content.replace(old_service, new_service, 1)

with open(MANIFEST, "w", encoding="utf-8") as f:
    f.write(content)

print("[ok] AndroidManifest.xml: CallForegroundService declared + foreground-service permissions added")
