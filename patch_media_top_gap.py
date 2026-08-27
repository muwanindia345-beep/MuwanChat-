p = "app/src/main/java/com/muwan/muwanchat/screens/MediaScreen.kt"
s = open(p, encoding="utf-8").read()

old_videos = '''private fun VideosList(messages: List<MessageEntity>, onTap: (MessageEntity) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {'''
new_videos = '''private fun VideosList(messages: List<MessageEntity>, onTap: (MessageEntity) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {'''

old_docs = '''private fun DocumentsList(messages: List<MessageEntity>, onTap: (MessageEntity) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {'''
new_docs = '''private fun DocumentsList(messages: List<MessageEntity>, onTap: (MessageEntity) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {'''

changed = False
if old_videos in s:
    s = s.replace(old_videos, new_videos)
    print("VideosList: top gap reduced")
    changed = True
elif "top = 4.dp" in s and "spacedBy(8.dp)" in s:
    print("VideosList: already fixed")

if old_docs in s:
    s = s.replace(old_docs, new_docs)
    print("DocumentsList: top gap reduced")
    changed = True
elif "top = 4.dp" in s and "spacedBy(4.dp)" in s:
    print("DocumentsList: already fixed")

if changed:
    open(p, "w", encoding="utf-8").write(s)

print("DONE")
