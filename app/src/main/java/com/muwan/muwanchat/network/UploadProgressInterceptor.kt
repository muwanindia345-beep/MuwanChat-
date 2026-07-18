package com.muwan.muwanchat.network

import com.muwan.muwanchat.data.UploadProgressTracker
import okhttp3.Interceptor
import okhttp3.Response

// ChatApi.uploadMedia() / uploadVideo() ke "X-Upload-Track-Id" header ko dekh kar
// request body ko CountingRequestBody se wrap kar deta hai taaki UploadProgressTracker
// me real % update hote rahe. Header khud server ko forward nahi hota (removeHeader).
class UploadProgressInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val uploadId = original.header("X-Upload-Track-Id")
        val body = original.body

        return if (uploadId != null && body != null) {
            val trackedRequest = original.newBuilder()
                .removeHeader("X-Upload-Track-Id")
                .method(
                    original.method,
                    CountingRequestBody(body) { percent ->
                        UploadProgressTracker.update(uploadId, percent)
                    }
                )
                .build()
            chain.proceed(trackedRequest)
        } else {
            chain.proceed(original)
        }
    }
}
