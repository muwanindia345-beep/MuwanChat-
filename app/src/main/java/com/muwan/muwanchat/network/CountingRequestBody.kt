package com.muwan.muwanchat.network

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
