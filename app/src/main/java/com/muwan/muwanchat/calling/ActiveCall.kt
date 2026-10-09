package com.muwan.muwanchat.calling

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.SocketEvent
import org.webrtc.VideoSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class CallPhase { IDLE, RINGING_OUTGOING, RINGING_INCOMING, CONNECTING, ONGOING }

data class ActiveCallInfo(
    val callId: String,
    val otherUid: String,
    val otherUsername: String,
    val callType: String,
    val isIncoming: Boolean
)

/**
 * App-level call controller (STEP 1: sirf yeh file add hui hai, abhi koi
 * isko use nahi kar raha -- isliye app ka behaviour bilkul same hai).
 * v2: CALL_PUSH_PATCH (PushRinger, Answer-from-notification, declinedCallIds,
 * screenCallId) ke saath kaam karne ke liye adjusted.
 *
 * Idea: call ki saari state/logic (CallManager, ringtone, mute/speaker,
 * timer, socket events) CallScreen ke andar nahi, yahan rahegi. Tab screen
 * band ho ya app ke kisi bhi screen pe jao, call chalti rahegi. CallScreen
 * (Step 2) sirf iski state dikhayega aur buttons yahan forward karega.
 *
 * RULE: saare public functions Main thread se hi call karne hain.
 * WebRTC ke background-thread callbacks yahan mainHandler se Main pe aate hain.
 */
object ActiveCall {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var appContext: Context? = null
    private var started = false

    private val _phase = MutableStateFlow(CallPhase.IDLE)
    val phase: StateFlow<CallPhase> = _phase.asStateFlow()

    private val _info = MutableStateFlow<ActiveCallInfo?>(null)
    val info: StateFlow<ActiveCallInfo?> = _info.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    // ───────── VIDEO_P2 state ─────────
    private val _cameraOn = MutableStateFlow(true)
    val cameraOn: StateFlow<Boolean> = _cameraOn.asStateFlow()

    private val _frontCamera = MutableStateFlow(true)
    val frontCamera: StateFlow<Boolean> = _frontCamera.asStateFlow()

    private val _remoteVideoAvailable = MutableStateFlow(false)
    val remoteVideoAvailable: StateFlow<Boolean> = _remoteVideoAvailable.asStateFlow()

    private val _remoteCameraOn = MutableStateFlow(true)
    val remoteCameraOn: StateFlow<Boolean> = _remoteCameraOn.asStateFlow()

    // Renderers handed over by the UI. Kept here so a fresh CallManager gets them too.
    private var remoteVideoSink: VideoSink? = null
    private var localVideoSink: VideoSink? = null
    // ───────── /VIDEO_P2 state ─────────

    // SystemClock.elapsedRealtime() jab call connect hui (0 = abhi nahi).
    // Timer isi se nikalta hai: screen ho ya banner, dono hamesha sync.
    private val _connectedAt = MutableStateFlow(0L)
    val connectedAt: StateFlow<Long> = _connectedAt.asStateFlow()

    private var callManager: CallManager? = null
    private var ringtone: CallRingtoneManager? = null
    private var pendingOfferSdp: String? = null
    private var serviceShown = false

    val isActive: Boolean get() = _info.value != null

    /** App start pe ek baar (MuwanChatApp.onCreate) -- socket events sunna shuru. */
    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        scope.launch {
            AppSocketManager.events.collect { handleSocketEvent(it) }
        }
        // Notification se Decline dabaya -- reject service bhej chuki hai,
        // yahan sirf local cleanup karna hai.
        scope.launch {
            CallControlEvents.declinedFromNotification.collect { declinedId ->
                if (_info.value?.callId == declinedId) finish()
            }
        }
    }

    // ─────────────────────────── Public actions ───────────────────────────

    /** Outgoing call. Mic permission pehle se mil chuki honi chahiye. */
    fun startOutgoing(otherUid: String, otherUsername: String, callType: String): Boolean {
        val ctx = appContext ?: return false
        if (_info.value != null) return false

        val info = ActiveCallInfo(
            callId = UUID.randomUUID().toString(),
            otherUid = otherUid,
            otherUsername = otherUsername,
            callType = callType,
            isIncoming = false
        )
        beginSession(ctx, info, CallPhase.RINGING_OUTGOING)
        val cm = callManager ?: return false
        cm.init()
        showOngoing() // STEP4A_ONGOING_NOTIF
        cm.createOffer { sdp ->
            AppSocketManager.sendCallOffer(info.callId, otherUid, callType, sdp) { success, error ->
                if (!success) {
                    mainHandler.post {
                        if (_info.value?.callId == info.callId) {
                            toast(if (error == "busy") "User is on another call" else "Could not start call")
                            finish()
                        }
                    }
                }
            }
        }
        return true
    }

    /** Incoming call. Mic permission ki zaroorat accept() se pehle padegi, yahan nahi. */
    fun onIncomingOffer(
        callId: String,
        fromUid: String,
        fromUsername: String,
        callType: String,
        sdp: String
    ): Boolean {
        val ctx = appContext ?: return false
        // Notification se decline ho chuki call ka offer replay aaye to ignore
        if (CallControlEvents.declinedCallIds.contains(callId)) return false
        val cur = _info.value
        if (cur != null) {
            // Same offer dobara aaya to ignore, warna hum already kisi call mein hain
            if (cur.callId != callId) AppSocketManager.sendCallReject(callId)
            return false
        }
        pendingOfferSdp = sdp
        beginSession(
            ctx,
            ActiveCallInfo(callId, fromUid, fromUsername, callType, true),
            CallPhase.RINGING_INCOMING
        )
        // FCM push is call ko dobara ring na kare (pehle CallScreen yeh set karti thi)
        CallControlEvents.screenCallId = callId
        // Answer notification se ho chuka ho to dobara ring-notification mat dikhao
        if (CallControlEvents.answerRequestedCallId != callId) {
            serviceShown = true
            try {
                CallForegroundService.showIncomingCall(ctx, callId, fromUsername)
            } catch (_: Exception) {
                serviceShown = false
            }
        }
        return true
    }

    /** Incoming call accept. Mic permission pehle se mil chuki honi chahiye. */
    fun accept() {
        val info = _info.value ?: return
        if (!info.isIncoming || _phase.value != CallPhase.RINGING_INCOMING) return
        val sdp = pendingOfferSdp ?: return
        val cm = callManager ?: return

        pendingOfferSdp = null
        PushRinger.stop()
        if (CallControlEvents.answerRequestedCallId == info.callId) {
            CallControlEvents.answerRequestedCallId = null
        }
        setPhase(CallPhase.CONNECTING)
        showOngoing() // STEP4A_ONGOING_NOTIF: incoming notification ki jagah ongoing notification
        cm.init()
        cm.createAnswer(sdp) { answerSdp ->
            mainHandler.post {
                if (_info.value?.callId == info.callId) {
                    AppSocketManager.sendCallAnswer(info.callId, answerSdp)
                    markConnected()
                }
            }
        }
    }

    /** Incoming call decline. */
    fun decline() {
        val info = _info.value ?: return
        AppSocketManager.sendCallReject(info.callId)
        finish()
    }

    /** Hang up. Ringing-incoming mein reject, baaki sab mein end bhejta hai. */
    fun end() {
        val info = _info.value ?: return
        if (_phase.value == CallPhase.RINGING_INCOMING) {
            AppSocketManager.sendCallReject(info.callId)
        } else {
            AppSocketManager.sendCallEnd(info.callId)
        }
        finish()
    }

    fun toggleMute() {
        val m = !_muted.value
        _muted.value = m
        callManager?.setMuted(m)
    }

    fun toggleSpeaker() {
        val s = !_speakerOn.value
        _speakerOn.value = s
        callManager?.setSpeakerOn(s)
    }

    // ───────── VIDEO_P2 actions ─────────
    val isVideoCall: Boolean get() = _info.value?.callType == "video"

    /** UI passes the renderer that shows the other person. Pass null when the UI goes away. */
    fun setRemoteVideoSink(sink: VideoSink?) {
        remoteVideoSink = sink
        callManager?.setRemoteVideoSink(sink)
    }

    /** UI passes the renderer for our own preview. Pass null when the UI goes away. */
    fun setLocalVideoSink(sink: VideoSink?) {
        localVideoSink = sink
        callManager?.setLocalVideoSink(sink)
    }

    fun toggleCamera() {
        val info = _info.value ?: return
        if (info.callType != "video") return
        val on = !_cameraOn.value
        _cameraOn.value = on
        callManager?.setCameraEnabled(on)
        if (_phase.value == CallPhase.ONGOING) AppSocketManager.sendCallMediaState(info.callId, on)
    }

    fun switchCamera() {
        if (!isVideoCall) return
        callManager?.switchCamera { isFront -> _frontCamera.value = isFront }
    }
    // ───────── /VIDEO_P2 actions ─────────

    // ─────────────────────────── Internals ───────────────────────────

    private fun handleSocketEvent(e: SocketEvent) {
        if (e is SocketEvent.CallOfferReceived) {
            onIncomingOffer(e.callId, e.fromUid, e.fromUsername, e.callType, e.sdp)
            return
        }
        val cur = _info.value ?: return
        when (e) {
            is SocketEvent.CallAnswerReceived -> {
                if (e.callId == cur.callId && !cur.isIncoming) {
                    callManager?.setRemoteAnswer(e.sdp)
                    markConnected()
                }
            }
            is SocketEvent.IceCandidateReceived -> {
                if (e.callId == cur.callId) {
                    callManager?.addRemoteIceCandidate(e.sdpMid, e.sdpMLineIndex, e.candidate)
                }
            }
            is SocketEvent.CallEndReceived -> {
                if (e.callId == cur.callId) finish()
            }
            is SocketEvent.CallBusyReceived -> {
                if (e.callId == cur.callId) {
                    toast("User is on another call")
                    finish()
                }
            }
            is SocketEvent.CallMediaStateReceived -> { // VIDEO_P2
                if (e.callId == cur.callId) _remoteCameraOn.value = e.camera
            }
            else -> {}
        }
    }

    private fun beginSession(ctx: Context, info: ActiveCallInfo, phase: CallPhase) {
        IceServerProvider.prefetch(ctx)
        _muted.value = false
        _speakerOn.value = false
        _cameraOn.value = true // VIDEO_P2
        _frontCamera.value = true
        _remoteVideoAvailable.value = false
        _remoteCameraOn.value = true
        _connectedAt.value = 0L
        _info.value = info
        callManager = CallManager(
            context = ctx,
            onLocalIceCandidate = { c ->
                AppSocketManager.sendIceCandidate(info.callId, c.sdpMid, c.sdpMLineIndex, c.sdp)
            },
            onRemoteAudioTrackAdded = { },
            onConnectionFailed = {
                mainHandler.post { handleConnectionFailed(info.callId) }
            },
            withVideo = info.callType == "video", // VIDEO_P2
            onRemoteVideoChanged = { available ->
                if (_info.value?.callId == info.callId) _remoteVideoAvailable.value = available
            }
        )
        callManager?.setRemoteVideoSink(remoteVideoSink)
        callManager?.setLocalVideoSink(localVideoSink)
        ringtone = CallRingtoneManager(ctx)
        setPhase(phase)
    }

    private fun setPhase(p: CallPhase) {
        _phase.value = p
        val r = ringtone
        when (p) {
            CallPhase.RINGING_INCOMING -> {
                // Push wali ring band karo; Answer notification se ho chuka ho to ring mat bajao
                PushRinger.stop()
                if (CallControlEvents.answerRequestedCallId != _info.value?.callId) {
                    r?.startIncomingRing()
                }
            }
            CallPhase.RINGING_OUTGOING -> r?.startOutgoingRingback()
            else -> r?.stop()
        }
    }

    private fun markConnected() {
        if (_phase.value == CallPhase.ONGOING) return
        _connectedAt.value = SystemClock.elapsedRealtime()
        setPhase(CallPhase.ONGOING)
        refreshOngoing() // STEP4A_ONGOING_NOTIF: notification mein timer chalu
        if (isVideoCall) { // VIDEO_P2
            // Video calls start on the speaker (the phone is held away from the ear)
            if (!_speakerOn.value) toggleSpeaker()
            // Camera was turned off while ringing: tell the other side now
            val ci = _info.value
            if (ci != null && !_cameraOn.value) AppSocketManager.sendCallMediaState(ci.callId, false)
        }
    }

    private fun handleConnectionFailed(callId: String) {
        if (_info.value?.callId != callId) return
        toast("Call connection failed (" + CallManager.lastConnectionState + ")")
        // Doosre side ko bhi batao, warna wo ringing/ongoing pe atka rehta hai
        AppSocketManager.sendCallEnd(callId)
        finish()
    }

    /** Sab cleanup, phir IDLE. Kai baar call hone par bhi safe. */
    private fun finish() {
        val endedInfo = _info.value
        ringtone?.stop()
        ringtone = null
        callManager?.release()
        callManager = null
        pendingOfferSdp = null
        resetAudio()
        if (endedInfo != null && endedInfo.isIncoming) {
            // Push se shuru hui ring/notification ko bhi hatao (service hamare track mein na ho tab bhi)
            PushRinger.stop()
            serviceShown = true
            if (CallControlEvents.screenCallId == endedInfo.callId) CallControlEvents.screenCallId = null
            if (CallControlEvents.answerRequestedCallId == endedInfo.callId) {
                CallControlEvents.answerRequestedCallId = null
            }
        }
        dismissServiceIfShown()
        _connectedAt.value = 0L
        _muted.value = false
        _speakerOn.value = false
        _cameraOn.value = true // VIDEO_P2
        _frontCamera.value = true
        _remoteVideoAvailable.value = false
        _remoteCameraOn.value = true
        _info.value = null
        _phase.value = CallPhase.IDLE
    }

    // STEP4A_ONGOING_NOTIF
    private fun showOngoing() {
        val ctx = appContext ?: return
        serviceShown = true
        try {
            CallForegroundService.showOngoingCall(ctx)
        } catch (_: Exception) {
        }
    }

    private fun refreshOngoing() {
        val ctx = appContext ?: return
        if (!serviceShown) return
        try {
            CallForegroundService.updateOngoing(ctx)
        } catch (_: Exception) {
        }
    }

    private fun dismissServiceIfShown() {
        if (!serviceShown) return
        serviceShown = false
        val ctx = appContext ?: return
        try {
            CallForegroundService.dismiss(ctx, true)
        } catch (_: Exception) {
        }
    }

    // Speaker toggle ke baad audio mode agli call/app mein na reh jaaye
    private fun resetAudio() {
        val ctx = appContext ?: return
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    private fun toast(msg: String) {
        val ctx = appContext ?: return
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
}
