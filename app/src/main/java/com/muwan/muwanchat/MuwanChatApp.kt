package com.muwan.muwanchat

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.muwan.muwanchat.data.AuthDataStore
import okhttp3.OkHttpClient

class MuwanChatApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AuthDataStore.wipeLegacyPlaintextStore(applicationContext)
        com.muwan.muwanchat.data.LocalMedia.init(applicationContext)
        com.muwan.muwanchat.calling.ActiveCall.start(applicationContext) // STEP2_ACTIVE_CALL
        com.muwan.muwanchat.calling.ActiveGroupCall.start(applicationContext) // GROUP_CALL_V1_ANDROID
        com.muwan.muwanchat.calling.IceServerProvider.prefetch(applicationContext)
        com.muwan.muwanchat.network.BackendKeepAlive.start(this) // KEEPALIVE_V1
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // SEC_FIX_V1: token sirf apne backend ke URL par
                val token = if (com.muwan.muwanchat.network.BackendHost.isBackend(chain.request().url))
                    AuthDataStore.getTokenBlocking(applicationContext) else ""
                val request = if (token.isNotEmpty()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else chain.request()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(120)
            .components {
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
