package com.muwan.muwanchat

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

// Video message bubbles ke liye Coil ko sikhana padta hai video se ek frame nikalna —
// warna wo sirf default fallback (blank/broken) dikhata hai jaisa image ke liye karta hai.
class MuwanChatApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}
