import io, os

files = {
    "app/src/main/java/com/muwan/muwanchat/data/UploadProgressTracker.kt": """package com.muwan.muwanchat.data

import androidx.compose.runtime.mutableStateMapOf

// Real-time upload % track karne ke liye — messageId -> 0..100
// mutableStateMapOf hone ki wajah se MessageBubble automatically recompose
// hoga jaise jaise % change hota hai, koi manual callback/flow chahiye nahi.
object UploadProgressTracker {
    val progress = mutableStateMapOf<String, Int>()

    fun start(id: String) {
        progress[id] = 0
    }

    fun update(id: String, percent: Int) {
        progress[id] = percent.coerceIn(0, 100)
    }

    fun clear(id: String) {
        progress.remove(id)
    }
}
""",
    "app/src/main/java/com/muwan/muwanchat/network/CountingRequestBody.kt": """package com.muwan.muwanchat.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

// Asal RequestBody ko wrap karke uske actual network write bytes count karta hai —
// isse "compression/encoding" wale fake progress ki jagah real upload % milta hai.
class CountingRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (percent: Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink, contentLength(), onProgress)
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    private class CountingSink(
        delegate: Sink,
        private val contentLength: Long,
        private val onProgress: (Int) -> Unit
    ) : ForwardingSink(delegate) {
        private var bytesWritten = 0L
        private var lastPercent = -1

        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            if (contentLength > 0) {
                val percent = ((bytesWritten * 100) / contentLength).toInt().coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent)
                }
            }
        }
    }
}
""",
    "app/src/main/java/com/muwan/muwanchat/network/UploadProgressInterceptor.kt": """package com.muwan.muwanchat.network

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
""",
    "app/src/main/java/com/muwan/muwanchat/util/NetworkUtils.kt": """package com.muwan.muwanchat.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

// Upload shuru karne se pehle quick check — agar network hi nahi hai to
// hum seedha FAILED maar denge, koi "uploading" progress layer nahi dikhayenge.
fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
}
""",
}

for path, content in files.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Created", path)
