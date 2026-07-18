import sys
path = "app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old = '''                previewTitle = it.link_preview?.title,
                previewDescription = it.link_preview?.description,
                previewImage = it.link_preview?.image,
                previewUrl = it.link_preview?.url
            )
        }
        db.messageDao().insertAll(entities)'''
new = '''                previewTitle = it.link_preview?.title,
                previewDescription = it.link_preview?.description,
                previewImage = it.link_preview?.image,
                previewUrl = it.link_preview?.url,
                isForwarded = it.is_forwarded
            )
        }
        db.messageDao().insertAll(entities)'''
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatRepository.kt patched: syncMessages ab isForwarded persist karta hai")
