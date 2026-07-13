path = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"
with open(path) as f:
    content = f.read()

old = '''private fun linkifyText(text: String, sent: Boolean): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    val matcher = Patterns.WEB_URL.matcher(text)
    val linkColor = if (sent) LinkColorOnSent else DarkAccent
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        var url = text.substring(start, end)
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        builder.addStyle(
            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            start, end
        )
        builder.addStringAnnotation(tag = LINK_TAG, annotation = url, start = start, end = end)
    }
    return builder.toAnnotatedString()
}'''

new = '''// muwanchat://join/{code} jaisa custom-scheme invite link Patterns.WEB_URL se
// match nahi hota (wo sirf http/https/www samajhta hai), isliye alag se
// regex chahiye. Overlap avoid karne ke liye already-covered ranges track
// karte hain taaki WEB_URL match par dobara annotation na lage.
private val CUSTOM_SCHEME_LINK = Regex("""muwanchat://\\S+""")

private fun linkifyText(text: String, sent: Boolean): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    val linkColor = if (sent) LinkColorOnSent else DarkAccent
    val coveredRanges = mutableListOf<IntRange>()

    val matcher = Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        var url = text.substring(start, end)
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        builder.addStyle(
            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            start, end
        )
        builder.addStringAnnotation(tag = LINK_TAG, annotation = url, start = start, end = end)
        coveredRanges.add(start until end)
    }

    CUSTOM_SCHEME_LINK.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (coveredRanges.none { it.first < end && start < it.last + 1 }) {
            builder.addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                start, end
            )
            builder.addStringAnnotation(tag = LINK_TAG, annotation = match.value, start = start, end = end)
        }
    }

    return builder.toAnnotatedString()
}'''

assert old in content, "linkifyText block not found — MessageBubble.kt structure changed"
content = content.replace(old, new, 1)

with open(path, "w") as f:
    f.write(content)

print("MessageBubble.kt patched: muwanchat:// custom-scheme links ab clickable honge")
