package com.muwan.muwanchat.calling

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import org.webrtc.AudioSource
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
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
    private val onConnectionFailed: () -> Unit,
    // VIDEO_P1: video is opt-in per call; defaults keep existing callers audio-only
    private val withVideo: Boolean = false,
    private val onRemoteVideoChanged: (Boolean) -> Unit = {}
) {
    private val stateLock = Any()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var connState: PeerConnection.PeerConnectionState? = null

    companion object {
        // Sirf diagnostics ke liye: aakhri connection state ka naam (CallScreen toast mein dikhata hai)
        @Volatile var lastConnectionState: String = ""
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null

    // VIDEO_P1 state. Mutable references are guarded by videoLock.
    private val videoLock = Any()
    @Volatile private var closed = false
    @Volatile private var frontCamera = true
    @Volatile private var cameraWanted = true
    @Volatile private var capturing = false
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var localSink: VideoSink? = null
    private var remoteSink: VideoSink? = null
    private val cameraExecutor by lazy { Executors.newSingleThreadExecutor() }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val iceServers: List<PeerConnection.IceServer>
        get() = IceServerProvider.servers()

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
                        val trackKind = receiver.track()?.kind()
                        if (trackKind == "audio") onRemoteAudioTrackAdded()
                        if (trackKind == "video") handleRemoteVideoTrack(receiver.track() as? VideoTrack)
                    }
                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                        connState = newState
                        lastConnectionState = newState.name
                        android.util.Log.d("CallManager", "connection state: $newState")
                        when (newState) {
                            PeerConnection.PeerConnectionState.FAILED ->
                                mainHandler.post { onConnectionFailed() }
                            // DISCONNECTED aksar temporary hota hai (network blip) --
                            // 8 second wait karo, tab bhi na sudhre to hi call kaato
                            PeerConnection.PeerConnectionState.DISCONNECTED ->
                                mainHandler.postDelayed({
                                    if (connState == PeerConnection.PeerConnectionState.DISCONNECTED) {
                                        onConnectionFailed()
                                    }
                                }, 8000)
                            else -> {}
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
            if (withVideo) addLocalVideoTrackLocked() // VIDEO_P1
        }
    }

    /** Caller stateLock ke andar hi hona chahiye -- naam mein "Locked" isliye hai. */
    private fun addLocalAudioTrackLocked() {
        val factory = peerConnectionFactory ?: return
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio_track", audioSource)
        peerConnection?.addTrack(localAudioTrack, listOf("audio_stream"))
    }

    // ───────── VIDEO_P1 ─────────
    // Video call support.
    // Lock order is always stateLock -> videoLock, never the reverse. The
    // PeerConnection observer runs on the WebRTC signaling thread, which is
    // blocked while close()/dispose() runs under stateLock, so observer code
    // (handleRemoteVideoTrack) may only take videoLock, never stateLock.

    private val videoWidth = 640
    private val videoHeight = 480
    private val videoFps = 24

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator: CameraEnumerator =
            if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context)
            else Camera1Enumerator(false)
        val names = enumerator.deviceNames
        val front = names.firstOrNull { enumerator.isFrontFacing(it) }
        val back = names.firstOrNull { enumerator.isBackFacing(it) }
        val name = front ?: back ?: return null
        frontCamera = front != null
        return enumerator.createCapturer(name, null)
    }

    /** Must be called with stateLock held (it runs inside createPeerConnection). */
    private fun addLocalVideoTrackLocked() {
        val factory = peerConnectionFactory ?: return
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.w("CallManager", "CAMERA permission missing, continuing audio-only")
            return
        }
        val capturer = createCameraCapturer()
        if (capturer == null) {
            android.util.Log.w("CallManager", "no camera available, continuing audio-only")
            return
        }
        val helper = SurfaceTextureHelper.create("MuwanCaptureThread", WebRtcEngine.eglBase.eglBaseContext)
        val source = factory.createVideoSource(capturer.isScreencast)
        capturer.initialize(helper, context, source.capturerObserver)
        val track = factory.createVideoTrack("video_track", source)
        track.setEnabled(cameraWanted)
        peerConnection?.addTrack(track, listOf("video_stream"))
        synchronized(videoLock) {
            videoCapturer = capturer
            surfaceHelper = helper
            videoSource = source
            localVideoTrack = track
            if (!closed) localSink?.let { track.addSink(it) }
        }
        syncCapture()
    }

    /** Starts or stops the camera hardware so it matches `cameraWanted` (runs off the main thread). */
    private fun syncCapture() {
        val capturer = synchronized(videoLock) { videoCapturer } ?: return
        try {
            cameraExecutor.execute {
                try {
                    val shouldCapture = cameraWanted && !closed
                    if (shouldCapture && !capturing) {
                        capturer.startCapture(videoWidth, videoHeight, videoFps)
                        capturing = true
                    } else if (!shouldCapture && capturing) {
                        capturer.stopCapture()
                        capturing = false
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CallManager", "camera start/stop failed: ${e.message}")
                }
            }
        } catch (e: RejectedExecutionException) {
            // call already torn down, nothing to do
        }
    }

    /** Runs on the WebRTC signaling thread. Takes videoLock only. */
    private fun handleRemoteVideoTrack(track: VideoTrack?) {
        if (track == null) return
        synchronized(videoLock) {
            if (closed) return
            remoteVideoTrack = track
            remoteSink?.let { track.addSink(it) }
        }
        mainHandler.post { onRemoteVideoChanged(true) }
    }

    /** UI hands over (or removes with null) the renderer that shows the other person. */
    fun setRemoteVideoSink(sink: VideoSink?) {
        synchronized(videoLock) {
            if (closed) return
            val track = remoteVideoTrack
            remoteSink?.let { track?.removeSink(it) }
            remoteSink = sink
            if (sink != null) track?.addSink(sink)
        }
    }

    /** UI hands over (or removes with null) the renderer for the self-preview. */
    fun setLocalVideoSink(sink: VideoSink?) {
        synchronized(videoLock) {
            if (closed) return
            val track = localVideoTrack
            localSink?.let { track?.removeSink(it) }
            localSink = sink
            if (sink != null) track?.addSink(sink)
        }
    }

    /** Turns our camera on/off. Off also releases the camera hardware (privacy light goes out). */
    fun setCameraEnabled(enabled: Boolean) {
        val track: VideoTrack?
        synchronized(videoLock) {
            if (closed) return
            cameraWanted = enabled
            track = localVideoTrack
        }
        track?.setEnabled(enabled)
        syncCapture()
    }

    /** Front <-> back camera. onDone(isFront) is delivered on the main thread. */
    fun switchCamera(onDone: (Boolean) -> Unit = {}) {
        val capturer = synchronized(videoLock) { if (closed) null else videoCapturer } ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                frontCamera = isFrontCamera
                mainHandler.post { onDone(isFrontCamera) }
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                android.util.Log.w("CallManager", "camera switch failed: $errorDescription")
            }
        })
    }

    /** True while the front camera is active (the self-preview should be mirrored then). */
    fun isFrontCamera(): Boolean = frontCamera

    /** First step of teardown: no sink may touch a track after this returns. */
    private fun detachVideoSinks() {
        synchronized(videoLock) {
            closed = true
            try {
                remoteSink?.let { remoteVideoTrack?.removeSink(it) }
                localSink?.let { localVideoTrack?.removeSink(it) }
            } catch (e: Exception) {
                android.util.Log.w("CallManager", "detach sinks failed: ${e.message}")
            }
            remoteSink = null
            localSink = null
            remoteVideoTrack = null
        }
    }

    /** Last step of teardown: runs after the PeerConnection is disposed. */
    private fun releaseVideoResources() {
        val track: VideoTrack?
        val capturer: CameraVideoCapturer?
        val helper: SurfaceTextureHelper?
        val source: VideoSource?
        synchronized(videoLock) {
            track = localVideoTrack
            capturer = videoCapturer
            helper = surfaceHelper
            source = videoSource
            localVideoTrack = null
            videoCapturer = null
            surfaceHelper = null
            videoSource = null
        }
        if (track == null && capturer == null && helper == null && source == null) return
        try { track?.dispose() } catch (e: Exception) { }
        val teardown = Runnable {
            try {
                if (capturing) {
                    capturer?.stopCapture()
                    capturing = false
                }
            } catch (e: Exception) { }
            try { capturer?.dispose() } catch (e: Exception) { }
            try { helper?.dispose() } catch (e: Exception) { }
            try { source?.dispose() } catch (e: Exception) { }
        }
        try {
            cameraExecutor.execute(teardown)
            cameraExecutor.shutdown()
        } catch (e: RejectedExecutionException) {
            teardown.run()
        }
    }
    // ───────── /VIDEO_P1 ─────────

    /** Caller side: naya call shuru karte waqt offer banao */
    fun createOffer(onSdpReady: (String) -> Unit) {
        createPeerConnection()
        val constraints = MediaConstraints()
        // VIDEO_P2: even without our own camera we still want to see the other person
        if (withVideo) constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
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
        detachVideoSinks() // VIDEO_P1
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
        releaseVideoResources() // VIDEO_P1
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
