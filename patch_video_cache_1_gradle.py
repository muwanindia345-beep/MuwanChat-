import io
path = "app/build.gradle.kts"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old = """    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")"""
new = """    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-datasource:1.3.1")
    implementation("androidx.media3:media3-database:1.3.1")"""

assert content.count(old) == 1, "match failed"
content = content.replace(old, new)
with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched app/build.gradle.kts")
