package com.muwan.muwanchat.calling

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.SocketEvent
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
        dismissServiceIfShown()
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
            else -> {}
        }
    }

    private fun beginSession(ctx: Context, info: ActiveCallInfo, phase: CallPhase) {
        _muted.value = false
        _speakerOn.value = false
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
            }
        )
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
        _info.value = null
        _phase.value = CallPhase.IDLE
    }

    private fun dismissServiceIfShown() {
        if (!serviceShown) return
        serviceShown = false
        val ctx = appContext ?: return
        try {
            CallForegroundService.dismiss(ctx)
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
