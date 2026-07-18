import io
path = "app/src/main/java/com/muwan/muwanchat/screens/FullscreenVideoPlayer.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old = """import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.muwan.muwanchat.data.MediaSaver"""
new = """import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.muwan.muwanchat.data.MediaSaver
import com.muwan.muwanchat.data.VideoCacheProvider"""
assert content.count(old) == 1, "match failed: imports"
content = content.replace(old, new)

old = """    fun buildPlayer(ctx: android.content.Context): ExoPlayer {
        return ExoPlayer.Builder(ctx).build().apply {"""
new = """    fun buildPlayer(ctx: android.content.Context): ExoPlayer {
        // Cache-aware datasource — pehli baar network se video aata hai aur disk pe
        // cache ho jata hai, dobara wahi video play karo to seedha disk se milega
        // (offline bhi chalega, data bhi kam lagega)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(VideoCacheProvider.get(ctx))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build().apply {"""
assert content.count(old) == 1, "match failed: buildPlayer"
content = content.replace(old, new)

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched FullscreenVideoPlayer.kt")
