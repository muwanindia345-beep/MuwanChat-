import io, os

path = "app/src/main/java/com/muwan/muwanchat/data/VideoCacheProvider.kt"
content = """package com.muwan.muwanchat.data

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

// Ek hi SimpleCache instance pure app me chalta hai — media3 isi cache directory ko
// dobara kisi doosre SimpleCache se open nahi hone deta (crash aata hai), isliye
// singleton pattern zaroori hai. 200MB tak recent-most videos disk pe cache rahenge,
// usse zyada purane automatically evict (LRU) ho jayenge.
object VideoCacheProvider {
    private const val MAX_CACHE_BYTES = 200L * 1024 * 1024

    @Volatile private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: SimpleCache(
                File(context.applicationContext.cacheDir, "video_cache"),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { instance = it }
        }
    }
}
"""

os.makedirs(os.path.dirname(path), exist_ok=True)
with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Created", path)
