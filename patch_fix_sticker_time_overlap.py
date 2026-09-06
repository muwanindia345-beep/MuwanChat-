# -*- coding: utf-8 -*-
# Sticker/GIF bubble ka time+tick overlay Box(BottomEnd) se seedha sticker
# artwork ke upar baitha tha, isliye jab sticker/gif ka drawing bottom-right
# corner tak jaata hai to timestamp usse overlap/cut kar deta tha.
# Fix: Box overlay hata ke Column banaya -- sticker upar, time row uske
# NEECHE (right-aligned), taaki kabhi artwork ko touch na kare, sticker ka
# shape/aspect ratio kuch bhi ho.

path = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old = '''                    "gif" -> message.mediaUrl?.let { url ->
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Sticker",
                                placeholder = ColorPainter(Color(0xFF2A2A2A)),
                                error = ColorPainter(Color(0xFF2A2A2A)),
                                modifier = Modifier
                                    .sizeIn(minWidth = 140.dp, minHeight = 140.dp, maxWidth = 180.dp, maxHeight = 180.dp)
                                    .clickable { if (isSelectionMode) onTap() },
                                contentScale = ContentScale.Fit
                            )
                            Row(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x99000000))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(message.time, color = Color.White, fontSize = 10.sp)
                                if (message.sent) {
                                    Spacer(Modifier.width(3.dp))
                                    val (icon, tint) = when (message.status) {
                                        "UPLOADING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                                        "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                                        "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)
                                        "FAILED" -> Icons.Filled.ErrorOutline to Color(0xFFE53935)
                                        else -> Icons.Filled.Check to Color(0xAAFFFFFF)
                                    }
                                    Icon(icon, contentDescription = message.status, tint = tint, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }'''

new = '''                    "gif" -> message.mediaUrl?.let { url ->
                        Column(horizontalAlignment = Alignment.End) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Sticker",
                                placeholder = ColorPainter(Color(0xFF2A2A2A)),
                                error = ColorPainter(Color(0xFF2A2A2A)),
                                modifier = Modifier
                                    .sizeIn(minWidth = 140.dp, minHeight = 140.dp, maxWidth = 180.dp, maxHeight = 180.dp)
                                    .clickable { if (isSelectionMode) onTap() },
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x33000000))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(message.time, color = Color(0xAAFFFFFF), fontSize = 10.sp)
                                if (message.sent) {
                                    Spacer(Modifier.width(3.dp))
                                    val (icon, tint) = when (message.status) {
                                        "UPLOADING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                                        "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                                        "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)
                                        "FAILED" -> Icons.Filled.ErrorOutline to Color(0xFFE53935)
                                        else -> Icons.Filled.Check to Color(0xAAFFFFFF)
                                    }
                                    Icon(icon, contentDescription = message.status, tint = tint, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }'''

n = src.count(old)
if n != 1:
    raise SystemExit(f"[FAIL] sticker time overlap fix: found {n} matches (expected 1)")
src = src.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Sticker/GIF timestamp moved below artwork, no more overlap")
