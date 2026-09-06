# -*- coding: utf-8 -*-
# "Broadcast" 2 lines me wrap ho raha tha, isse nav bar taller ho gayi
# aur "+" FAB se overlap kar rahi thi. Fix: label ko hamesha single-line
# rakho, zaroorat pade to "..." (ellipsis) laga do -- baaki kuch bhi
# (colors, icon size, padding, pill shape) bilkul waisa hi rahega.

PATH = "app/src/main/java/com/muwan/muwanchat/screens/BottomNavBar.kt"

with open(PATH, "r", encoding="utf-8") as f:
    content = f.read()

old_import = 'import androidx.compose.material3.Text\n'
new_import = 'import androidx.compose.material3.Text\nimport androidx.compose.ui.text.style.TextOverflow\n'
if old_import not in content:
    raise SystemExit("[FAIL] Text import not found -- patch by hand")
content = content.replace(old_import, new_import, 1)

old_text = '''                Text(
                    tab.label,
                    color = if (selected) DarkAccent else Color(0xFF888888),
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )'''
new_text = '''                Text(
                    tab.label,
                    color = if (selected) DarkAccent else Color(0xFF888888),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )'''
if old_text not in content:
    raise SystemExit("[FAIL] Text block not found (font size may already differ) -- patch by hand")
content = content.replace(old_text, new_text, 1)

with open(PATH, "w", encoding="utf-8") as f:
    f.write(content)

print("[ok] BottomNavBar label: single-line + ellipsis, no more 2-line wrap/overlap")
