p = "app/src/main/java/com/muwan/muwanchat/screens/MediaScreen.kt"
s = open(p, encoding="utf-8").read()

start_marker = "indicator = { positions ->"
idx = s.find(start_marker)
if idx == -1:
    print("WARNING: indicator block start not found, check manually")
else:
    brace_open_idx = s.find("{", idx)
    depth = 0
    i = brace_open_idx
    end_idx = None
    while i < len(s):
        if s[i] == "{":
            depth += 1
        elif s[i] == "}":
            depth -= 1
            if depth == 0:
                end_idx = i + 1
                break
        i += 1
    if end_idx is None:
        print("WARNING: could not find matching closing brace, check manually")
    else:
        new_block = '''indicator = { positions ->
                if (pagerState.currentPage < positions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.fillMaxWidth()
                            .wrapContentSize(align = Alignment.BottomStart)
                            .offset(x = positions[pagerState.currentPage].left)
                            .width(positions[pagerState.currentPage].width),
                        color = DarkAccent
                    )
                }
            }'''
        old_block = s[idx:end_idx]
        if old_block.strip() == new_block.strip():
            print("MediaScreen.kt: indicator block already correct, no change needed")
        else:
            s = s[:idx] + new_block + s[end_idx:]
            open(p, "w", encoding="utf-8").write(s)
            print("MediaScreen.kt: indicator block replaced with tabIndicatorOffset-free version")

print("DONE")
