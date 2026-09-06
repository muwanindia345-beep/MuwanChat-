package com.muwan.muwanchat.calling

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * WebRTC ka rule hai: `PeerConnectionFactory.initialize()` poori app process
 * ki life mein SIRF EK BAAR call hona chahiye, aur factory instance khud bhi
 * reuse honi chahiye (dobara-dobara nahi banani). Pehle CallManager har naye
 * call pe (naya CallManager banate waqt) yeh dobara kar raha tha -- doosri/
 * teesri call pe native engine (`libjingle_peerconnection_so.so`) ka internal
 * safety-check fail ho jaata tha aur `SIGTRAP` crash deta tha (release build
 * mein pakड़ में aaya, Crashlytics NDK report se confirm hua).
 *
 * NOTE: yeh us doosre SIGTRAP fix (CallManager ka `stateLock`, dispose/use
 * race ke liye) se ALAG bug hai -- dono independent issues the, dono chahiye.
 *
 * Ab factory yahan ek app-wide singleton hai -- ek baar banti hai, sabhi
 * calls (chahe kitni bhi ho) usi ko reuse karti hain. Kabhi dispose nahi hoti
 * (process khatam hone tak zinda rehti hai, jo WebRTC ke liye normal hai).
 */
object WebRtcEngine {
    @Volatile
    private var factory: PeerConnectionFactory? = null

    private val eglBase: EglBase by lazy { EglBase.create() }

    @Synchronized
    fun getFactory(context: Context): PeerConnectionFactory {
        factory?.let { return it }

        val appContext = context.applicationContext
        val options = PeerConnectionFactory.InitializationOptions.builder(appContext)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val adm = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val newFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        adm.release()
        factory = newFactory
        return newFactory
    }
}
