package com.muwan.muwanchat

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.muwan.muwanchat.data.AuthDataStore
import okhttp3.OkHttpClient

class MuwanChatApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AuthDataStore.wipeLegacyPlaintextStore(applicationContext)
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = AuthDataStore.getTokenBlocking(applicationContext)
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
