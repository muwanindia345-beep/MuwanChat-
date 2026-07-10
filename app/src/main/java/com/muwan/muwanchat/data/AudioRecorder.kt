package com.muwan.muwanchat.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

// Voice message recorder — MediaRecorder ko wrap karta hai, live amplitude aur
// elapsed time StateFlow se expose karta hai taaki bottom sheet ka waveform +
// timer real-time update ho sake.
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var pollJob: Job? = null
    private var isPaused = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude

    private val _elapsedMillis = MutableStateFlow(0)
    val elapsedMillis: StateFlow<Int> = _elapsedMillis

    fun start(): File {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
        } catch (_: Exception) {
            try { r.release() } catch (_: Exception) {}
            recorder = null
        }
        isPaused = false
        _elapsedMillis.value = 0
        _amplitude.value = 0
        startPolling()
        return file
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var ms = 0
            while (isActive) {
                delay(100)
                if (!isPaused && recorder != null) {
                    ms += 100
                    _elapsedMillis.value = ms
                    _amplitude.value = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                }
            }
        }
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                recorder?.pause()
                isPaused = true
            } catch (_: Exception) {}
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                recorder?.resume()
                isPaused = false
            } catch (_: Exception) {}
        }
    }

    // Recording rok ke file return karta hai — success pe hi non-null
    fun stopAndGetFile(): File? {
        pollJob?.cancel()
        val file = outputFile
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            file
        } catch (_: Exception) {
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            null
        }
    }

    fun cancel() {
        pollJob?.cancel()
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
