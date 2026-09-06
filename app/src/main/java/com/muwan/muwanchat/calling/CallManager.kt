package com.muwan.muwanchat.calling

import android.content.Context
import android.media.AudioManager
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * WebRTC ka poora kaam yahan hota hai -- naya PeerConnection banana (per-call),
 * local audio track, offer/answer create karna, ICE candidates handle karna.
 * CallScreen.kt sirf iske callbacks use karta hai, WebRTC ke internal types se
 * seedha waasta nahi rakhta -- taaki UI clean rahe aur video call (Phase 2)
 * add karte waqt yeh class hi extend ho, UI dobara na likhni pade.
 *
 * NOTE: PeerConnectionFactory yahan nahi banti -- woh `WebRtcEngine` (app-wide
 * singleton) se aati hai, kyunki WebRTC ka rule hai factory poori app life
 * mein sirf ek baar banni chahiye. Pehle yeh per-call dobara ban rahi thi,
 * jisse doosri/teesri call pe native SIGTRAP crash aata tha.
 *
 * NOTE (TURN): Abhi sirf free public STUN hai. Cross-network (jaise ek WiFi
 * ek mobile-data) call test karne se pehle Metered.ca ka free Open Relay
 * TURN account bana ke neeche TURN_USERNAME/TURN_CREDENTIAL fill karo --
 * warna restrictive networks pe call connect nahi hogi.
 *
 * THREAD SAFETY: peerConnection/localAudioTrack/audioSource ko UI thread
 * (mute/speaker/end-call buttons) aur WebRTC ke apne signaling thread
 * (onConnectionChange -> auto end call) dono touch karte hain. `stateLock`
 * ke bina dispose-then-use race lag sakti thi (already-freed native object
 * pe method call -> native SIGTRAP crash). Isliye har jagah jaha inhe
 * padha/badla jaata hai, stateLock ke andar hi hota hai.
 */
class CallManager(
    private val context: Context,
    private val onLocalIceCandidate: (IceCandidate) -> Unit,
    private val onRemoteAudioTrackAdded: () -> Unit,
    private val onConnectionFailed: () -> Unit
) {
    private val stateLock = Any()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null

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
        val factory = WebRtcEngine.getFactory(context)
        synchronized(stateLock) {
            peerConnectionFactory = factory
        }
    }


    /** Naya PeerConnection banata hai (caller aur callee dono ke liye same) */
    private fun createPeerConnection() {
        synchronized(stateLock) {
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
            addLocalAudioTrackLocked()
        }
    }

    /** Caller stateLock ke andar hi hona chahiye -- naam mein "Locked" isliye hai. */
    private fun addLocalAudioTrackLocked() {
        val factory = peerConnectionFactory ?: return
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio_track", audioSource)
        peerConnection?.addTrack(localAudioTrack, listOf("audio_stream"))
    }

    /** Caller side: naya call shuru karte waqt offer banao */
    fun createOffer(onSdpReady: (String) -> Unit) {
        createPeerConnection()
        val constraints = MediaConstraints()
        synchronized(stateLock) {
            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    synchronized(stateLock) {
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                    }
                    onSdpReady(desc.description)
                }
                override fun onCreateFailure(error: String?) {}
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
    }

    /** Callee side: aaya hua offer set karke answer banao */
    fun createAnswer(remoteSdp: String, onSdpReady: (String) -> Unit) {
        createPeerConnection()
        synchronized(stateLock) {
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
                    synchronized(stateLock) {
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                    }
                    onSdpReady(desc.description)
                }
                override fun onCreateFailure(error: String?) {}
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
    }

    /** Caller side: callee ka answer aaya, use apply karo */
    fun setRemoteAnswer(remoteSdp: String) {
        synchronized(stateLock) {
            peerConnection?.setRemoteDescription(
                object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        flushPendingCandidates()
                    }
                },
                SessionDescription(SessionDescription.Type.ANSWER, remoteSdp)
            )
        }
    }

    // Jab tak remote description set nahi hua (caller ke liye: jab tak answer
    // nahi aaya; callee ke liye: jab tak offer set nahi hua), aane waale ICE
    // candidates yahan queue ho jaate hain -- warna woh silently drop ho jaate
    // the aur call connect hone mein dikkat aati thi.
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    /** Caller stateLock ke andar hi hona chahiye. */
    private fun flushPendingCandidates() {
        remoteDescriptionSet = true
        pendingRemoteCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    fun addRemoteIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        synchronized(stateLock) {
            if (!remoteDescriptionSet) {
                pendingRemoteCandidates.add(ice)
            } else {
                peerConnection?.addIceCandidate(ice)
            }
        }
    }

    fun setMuted(muted: Boolean) {
        synchronized(stateLock) {
            localAudioTrack?.setEnabled(!muted)
        }
    }

    fun setSpeakerOn(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
        audioManager.mode = if (on) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_IN_COMMUNICATION
    }

    fun endCall() {
        synchronized(stateLock) {
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
    }

    fun release() {
        endCall()
        synchronized(stateLock) {
            // Factory ab shared singleton (WebRtcEngine) hai -- yahan
            // dispose NAHI karna, warna agli call ke liye bhi tut jaayegi.
            peerConnectionFactory = null
        }
    }
}

/** SdpObserver ke saare methods override karne se bachne ke liye chhota helper */
private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
