f = "app/build.gradle.kts"
s = open(f).read()
old = '''freeCompilerArgs += listOf("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")'''
new = '''freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )'''
assert old in s, "pattern not found — check app/build.gradle.kts manually"
open(f, "w").write(s.replace(old, new, 1))
print("✅ build.gradle.kts patched — ExperimentalFoundationApi opt-in added")
