path = "app/src/main/AndroidManifest.xml"
with open(path) as f:
    content = f.read()

marker = '''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

        </activity>'''

new_block = '''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Group invite deep link: muwanchat://join/{code} -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="muwanchat" android:host="join" />
            </intent-filter>

        </activity>'''

assert marker in content, "manifest marker not found"
content = content.replace(marker, new_block, 1)

with open(path, "w") as f:
    f.write(content)

print("AndroidManifest.xml patched: muwanchat://join deep link intent-filter added")
