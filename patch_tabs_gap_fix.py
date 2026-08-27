p = "app/src/main/java/com/muwan/muwanchat/screens/MediaScreen.kt"
s = open(p, encoding="utf-8").read()

old1 = 'private val mediaTabs = listOf("Photos", "Videos", "Documents")'
new1 = 'private val mediaTabs = listOf("Photos", "Videos", "Docs")'
if old1 in s:
    s = s.replace(old1, new1)
    print("mediaTabs: 'Documents' shortened to 'Docs' so it fits on one line")
elif new1 in s:
    print("mediaTabs: already shortened, skipped")
else:
    print("WARNING: mediaTabs anchor not found")

old2 = '''                    text = {
                        Text(
                            title,
                            color = if (pagerState.currentPage == index) DarkAccent else Color(0xFF888888),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }'''
new2 = '''                    text = {
                        Text(
                            title,
                            color = if (pagerState.currentPage == index) DarkAccent else Color(0xFF888888),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }'''
if old2 in s:
    s = s.replace(old2, new2)
    print("Tab text: forced single-line (maxLines=1, softWrap=false) as future safety")
elif "softWrap = false" in s and "maxLines = 1" in s:
    print("Tab text: already single-line safe, skipped")
else:
    print("WARNING: Tab text anchor not found")

open(p, "w", encoding="utf-8").write(s)
print("DONE")
