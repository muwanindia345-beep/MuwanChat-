import io
path = "app/src/main/java/com/muwan/muwanchat/network/RetrofitClient.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old = """    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()"""
new = """    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(UploadProgressInterceptor())
        .build()"""

assert content.count(old) == 1, "match failed"
content = content.replace(old, new)
with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched RetrofitClient.kt")
