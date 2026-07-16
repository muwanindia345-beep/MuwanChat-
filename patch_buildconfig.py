import sys

path = "app/build.gradle.kts"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

anchor = """    buildFeatures {
        compose = true"""

new_block = """    buildFeatures {
        compose = true
        buildConfig = true"""

count = content.count(anchor)
if count == 0:
    print("ERROR: anchor not found — manual check karo.")
    sys.exit(1)
if count > 1:
    print(f"ERROR: anchor {count} baar mila, unique nahi hai — manual check karo.")
    sys.exit(1)

content = content.replace(anchor, new_block, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ build.gradle.kts me buildConfig = true add ho gaya.")
