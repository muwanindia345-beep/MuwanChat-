package com.muwan.muwanchat.calling

import android.content.Context
import android.media.AudioManager
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * WebRTC ka poora kaam yahan hota hai -- PeerConnectionFactory init, local
 * audio track banana, offer/answer create karna, ICE candidates handle
 * karna. CallScreen.kt sirf iske callbacks use karta hai, WebRTC ke internal
 * types se seedha waasta nahi rakhta -- taaki UI clean rahe aur video call
 * (Phase 2) add karte waqt yeh class hi extend ho, UI dobara na likhni pade.
 *
 * NOTE (TURN): Abhi sirf free public STUN hai. Cross-network (jaise ek WiFi
 * ek mobile-data) call test karne se pehle Metered.ca ka free Open Relay
 * TURN account bana ke neeche TURN_USERNAME/TURN_CREDENTIAL fill karo --
 * warna restrictive networks pe call connect nahi hogi.
 */
class CallManager(
    private val context: Context,
    private val onLocalIceCandidate: (IceCandidate) -> Unit,
    private val onRemoteAudioTrackAdded: () -> Unit,
    private val onConnectionFailed: () -> Unit
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private val eglBase: EglBase by lazy { EglBase.create() }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // TODO: Metered.ca "Open Relay Project" se free account banao aur yeh
    // 2 values fill karo (20GB/month free, no credit card required).
    private val TURN_USERNAME = "REPLACE_WITH_METERED_USERNAME"
    private val TURN_CREDENTIAL = "REPLACE_WITH_METERED_CREDENTIAL"

    private val iceServers: List<PeerConnection.IceServer>
        get() = listOf(
            PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
                .setUsername(TURN_USERNAME)
                .setPassword(TURN_CREDENTIAL)
                .createIceServer()
        )

    fun init() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        adm.release()
    }

    /** Naya PeerConnection banata hai (caller aur callee dono ke liye same) */
    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    onLocalIceCandidate(candidate)
                }
                override fun onAddTrack(
                    receiver: org.webrtc.RtpReceiver,
                    streams: Array<out org.webrtc.MediaStream>
                ) {
                    if (receiver.track()?.kind() == "audio") onRemoteAudioTrackAdded()
                }
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    if (newState == PeerConnection.PeerConnectionState.FAILED ||
                        newState == PeerConnection.PeerConnectionState.DISCONNECTED
                    ) {
                        onConnectionFailed()
                    }
                }
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onAddStream(p0: org.webrtc.MediaStream?) {}
                override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
                override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
                override fun onRenegotiationNeeded() {}
            }
        )
        addLocalAudioTrack()
    }

    private fun addLocalAudioTrack() {
        val factory = peerConnectionFactory ?: return
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio_track", audioSource)
        peerConnection?.addTrack(localAudioTrack, listOf("audio_stream"))
    }

    /** Caller side: naya call shuru karte waqt offer banao */
    fun createOffer(onSdpReady: (String) -> Unit) {
        createPeerConnection()
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                onSdpReady(desc.description)
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /** Callee side: aaya hua offer set karke answer banao */
    fun createAnswer(remoteSdp: String, onSdpReady: (String) -> Unit) {
        createPeerConnection()
        peerConnection?.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    // Ab remote description set ho chuka hai, jo bhi ICE
                    // candidates ringing ke dauraan queue hue the woh ab
                    // safely apply ho sakte hain.
                    flushPendingCandidates()
                }
            },
            SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
        )
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                onSdpReady(desc.description)
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /** Caller side: callee ka answer aaya, use apply karo */
    fun setRemoteAnswer(remoteSdp: String) {
        peerConnection?.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    flushPendingCandidates()
                }
            },
            SessionDescription(SessionDescription.Type.ANSWER, remoteSdp)
        )
    }

    // Jab tak remote description set nahi hua (caller ke liye: jab tak answer
    // nahi aaya; callee ke liye: jab tak offer set nahi hua), aane waale ICE
    // candidates yahan queue ho jaate hain -- warna woh silently drop ho jaate
    // the aur call connect hone mein dikkat aati thi.
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    private fun flushPendingCandidates() {
        remoteDescriptionSet = true
        pendingRemoteCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    fun addRemoteIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        if (!remoteDescriptionSet) {
            pendingRemoteCandidates.add(ice)
        } else {
            peerConnection?.addIceCandidate(ice)
        }
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setSpeakerOn(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
        audioManager.mode = if (on) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_IN_COMMUNICATION
    }

    fun endCall() {
        localAudioTrack?.dispose()
        audioSource?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        localAudioTrack = null
        audioSource = null
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
    }

    fun release() {
        endCall()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }
}

/** SdpObserver ke saare methods override karne se bachne ke liye chhota helper */
private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
