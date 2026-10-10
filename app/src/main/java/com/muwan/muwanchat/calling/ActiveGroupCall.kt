package com.muwan.muwanchat.calling

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.SocketEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

// GROUP_CALL_V1_ANDROID (step A)

enum class GroupCallPhase { IDLE, RINGING_INCOMING, CONNECTING, ONGOING }

enum class PeerLink { CONNECTING, CONNECTED, FAILED }

data class GroupParticipant(
    val uid: String,
    val username: String,
    val isSelf: Boolean = false,
    val muted: Boolean = false,
    val speaking: Boolean = false,
    val link: PeerLink = PeerLink.CONNECTING
)

data class GroupCallSession(
    val callId: String,
    val roomId: String,
    val groupName: String,
    val callType: String,
    val startedByUid: String,
    val startedByUsername: String,
    val isIncoming: Boolean
)

/**
 * App-level controller for group calls (mesh topology): one PeerConnection per
 * other participant, each one owned by a CallManager from the 1:1 stack.
 *
 * Mesh rule (matches the backend): whoever JOINS sends an offer to everybody who
 * is already in the call; the people already in the call only answer.
 *
 * RULE: all public functions must be called from the Main thread. Socket ack
 * callbacks and WebRTC callbacks are moved to Main through mainHandler.
 *
 * This object is independent from ActiveCall (1:1). The two check each other so
 * the user can never be in a 1:1 call and a group call at the same time.
 */
object ActiveGroupCall {

    private const val RING_TIMEOUT_MS = 45_000L
    private const val SPEAKING_THRESHOLD = 0.03f
    private const val SPEAKING_HOLD_MS = 700L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var appContext: Context? = null
    private var started = false

    private val _phase = MutableStateFlow(GroupCallPhase.IDLE)
    val phase: StateFlow<GroupCallPhase> = _phase.asStateFlow()

    private val _session = MutableStateFlow<GroupCallSession?>(null)
    val session: StateFlow<GroupCallSession?> = _session.asStateFlow()

    // Self first, then the others in join order.
    private val _participants = MutableStateFlow<List<GroupParticipant>>(emptyList())
    val participants: StateFlow<List<GroupParticipant>> = _participants.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    // SystemClock.elapsedRealtime() when we got into the call (0 = not yet). Screen and banner share it.
    private val _connectedAt = MutableStateFlow(0L)
    val connectedAt: StateFlow<Long> = _connectedAt.asStateFlow()

    val inCall: Boolean get() = _session.value != null

    private val peers = HashMap<String, CallManager>()
    private var ringtone: CallRingtoneManager? = null
    private var ringTimeout: Runnable? = null
    private var speakingJob: Job? = null
    private var lastLocalActiveAt = 0L
    private var myUid = ""
    private var myUsername = ""

    /** Once at app start (MuwanChatApp.onCreate): begin listening to group call socket events. */
    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        scope.launch {
            AppSocketManager.events.collect { handleSocketEvent(it) }
        }
    }

    // ─────────────────────────── Public actions ───────────────────────────

    /** Start a new group voice call. Mic permission must already be granted. */
    fun startCall(roomId: String, groupName: String): Boolean {
        val ctx = appContext ?: return false
        if (_session.value != null || ActiveCall.isActive) return false

        val callId = "gc_" + UUID.randomUUID().toString()
        beginSession(
            ctx,
            GroupCallSession(callId, roomId, groupName, "voice", "", "", false),
            GroupCallPhase.CONNECTING
        )
        scope.launch {
            loadIdentity(ctx)
            if (_session.value?.callId != callId) return@launch
            _session.value = _session.value?.copy(startedByUid = myUid, startedByUsername = myUsername)
            _participants.value = listOf(selfParticipant())
            AppSocketManager.sendGroupCallStart(callId, roomId, "voice") { ok, error, _ ->
                mainHandler.post {
                    if (_session.value?.callId == callId) {
                        if (ok) {
                            markOngoing()
                        } else {
                            toast(errorText(error))
                            finish()
                        }
                    }
                }
            }
        }
        return true
    }

    /** Join a call that is already running (from the "Join" strip in the group chat). */
    fun joinExisting(callId: String, roomId: String, groupName: String, callType: String): Boolean {
        val ctx = appContext ?: return false
        if (_session.value != null || ActiveCall.isActive) return false
        beginSession(
            ctx,
            GroupCallSession(callId, roomId, groupName, callType.ifBlank { "voice" }, "", "", true),
            GroupCallPhase.CONNECTING
        )
        doJoin(ctx, callId)
        return true
    }

    /** Accept the incoming ring. Mic permission must already be granted. */
    fun join() {
        val ctx = appContext ?: return
        val s = _session.value ?: return
        if (_phase.value != GroupCallPhase.RINGING_INCOMING) return
        stopRing()
        _phase.value = GroupCallPhase.CONNECTING
        doJoin(ctx, s.callId)
    }

    /** Decline the incoming ring. Local only: the rest of the group is not affected. */
    fun decline() {
        if (_session.value == null) return
        finish()
    }

    /** Leave the call. The call continues for the others. */
    fun leave() {
        val s = _session.value ?: return
        if (_phase.value != GroupCallPhase.RINGING_INCOMING) {
            AppSocketManager.sendGroupCallLeave(s.callId)
        }
        finish()
    }

    fun toggleMute() {
        if (_session.value == null) return
        val m = !_muted.value
        _muted.value = m
        peers.values.forEach { it.setMuted(m) }
        updateParticipant(myUid) { it.copy(muted = m, speaking = false) }
        val s = _session.value
        if (s != null && _phase.value == GroupCallPhase.ONGOING) {
            AppSocketManager.sendGroupCallState(s.callId, m)
        }
    }

    fun toggleSpeaker() {
        if (_session.value == null) return
        val on = !_speakerOn.value
        _speakerOn.value = on
        applySpeaker(on)
    }

    // ─────────────────────────── Internals ───────────────────────────

    private fun doJoin(ctx: Context, callId: String) {
        scope.launch {
            loadIdentity(ctx)
            if (_session.value?.callId != callId) return@launch
            AppSocketManager.sendGroupCallJoin(callId) { ok, error, others ->
                mainHandler.post {
                    if (_session.value?.callId == callId) {
                        if (ok) {
                            val list = ArrayList<GroupParticipant>()
                            list.add(selfParticipant())
                            others.forEach {
                                list.add(GroupParticipant(it.uid, it.username, muted = it.muted))
                            }
                            _participants.value = list
                            markOngoing()
                            // We are the newcomer: we offer to everybody who is already inside.
                            others.forEach { connectToPeer(it.uid, it.username, true, null) }
                        } else {
                            toast(errorText(error))
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun handleSocketEvent(e: SocketEvent) {
        if (e is SocketEvent.GroupCallStarted) {
            onGroupCallStarted(e)
            return
        }
        val s = _session.value ?: return
        when (e) {
            is SocketEvent.GroupCallPeerJoined -> {
                if (e.callId == s.callId) ensureParticipant(e.uid, e.username)
            }
            is SocketEvent.GroupCallPeerLeft -> {
                if (e.callId == s.callId) removePeer(e.uid)
            }
            is SocketEvent.GroupCallSignal -> {
                if (e.callId == s.callId) onSignal(e)
            }
            is SocketEvent.GroupCallPeerState -> {
                if (e.callId == s.callId) updateParticipant(e.uid) { it.copy(muted = e.muted) }
            }
            is SocketEvent.GroupCallEnded -> {
                if (e.callId == s.callId) {
                    if (_phase.value == GroupCallPhase.ONGOING) toast("Call ended")
                    finish()
                }
            }
            else -> {}
        }
    }

    private fun onGroupCallStarted(e: SocketEvent.GroupCallStarted) {
        val ctx = appContext ?: return
        // Already busy (1:1 or group): ignore silently, never ring on top of a live call.
        if (_session.value != null || ActiveCall.isActive) return
        beginSession(
            ctx,
            GroupCallSession(
                e.callId, e.roomId, e.groupName.ifBlank { "Group" }, "voice",
                e.fromUid, e.fromUsername, true
            ),
            GroupCallPhase.RINGING_INCOMING
        )
        // A push may have started its own ring already: this one takes over.
        PushRinger.stop()
        ringtone = CallRingtoneManager(ctx).also { it.startIncomingRing() }
        val timeout = Runnable {
            if (_phase.value == GroupCallPhase.RINGING_INCOMING && _session.value?.callId == e.callId) finish()
        }
        ringTimeout = timeout
        mainHandler.postDelayed(timeout, RING_TIMEOUT_MS)
    }

    private fun onSignal(e: SocketEvent.GroupCallSignal) {
        val phase = _phase.value
        if (phase != GroupCallPhase.CONNECTING && phase != GroupCallPhase.ONGOING) return
        when (e.kind) {
            "offer" -> {
                val sdp = e.sdp ?: return
                ensureParticipant(e.fromUid, null)
                connectToPeer(e.fromUid, nameOf(e.fromUid), false, sdp)
            }
            "answer" -> {
                val sdp = e.sdp ?: return
                peers[e.fromUid]?.setRemoteAnswer(sdp)
            }
            "ice" -> {
                val cand = e.candidate ?: return
                peers[e.fromUid]?.addRemoteIceCandidate(e.sdpMid, e.sdpMLineIndex, cand)
            }
        }
    }

    private fun connectToPeer(uid: String, username: String, initiator: Boolean, remoteOfferSdp: String?) {
        val ctx = appContext ?: return
        val s = _session.value ?: return
        if (uid == myUid || peers.containsKey(uid)) return
        val callId = s.callId

        val cm = CallManager(
            context = ctx,
            onLocalIceCandidate = { c ->
                AppSocketManager.sendGroupCallIce(callId, uid, c.sdpMid, c.sdpMLineIndex, c.sdp)
            },
            onRemoteAudioTrackAdded = { },
            onConnectionFailed = { mainHandler.post { onPeerFailed(callId, uid) } },
            withVideo = false,
            onPeerConnected = {
                if (_session.value?.callId == callId) {
                    updateParticipant(uid) { it.copy(link = PeerLink.CONNECTED) }
                }
            }
        )
        peers[uid] = cm
        cm.init()
        if (initiator) {
            cm.createOffer { sdp -> AppSocketManager.sendGroupCallSignal(callId, uid, "offer", sdp) }
        } else if (remoteOfferSdp != null) {
            cm.createAnswer(remoteOfferSdp) { sdp ->
                AppSocketManager.sendGroupCallSignal(callId, uid, "answer", sdp)
            }
        }
        // The local mic track exists only after createOffer/createAnswer, so mute is applied now.
        if (_muted.value) cm.setMuted(true)
        if (username.isNotBlank()) updateParticipant(uid) { it.copy(username = username) }
    }

    private fun onPeerFailed(callId: String, uid: String) {
        if (_session.value?.callId != callId) return
        peers.remove(uid)?.let {
            try { it.release() } catch (_: Exception) { }
        }
        updateParticipant(uid) { it.copy(link = PeerLink.FAILED, speaking = false) }
    }

    private fun removePeer(uid: String) {
        peers.remove(uid)?.let {
            try { it.release() } catch (_: Exception) { }
        }
        _participants.value = _participants.value.filter { it.uid != uid }
    }

    private fun markOngoing() {
        if (_phase.value == GroupCallPhase.ONGOING) return
        _connectedAt.value = SystemClock.elapsedRealtime()
        _phase.value = GroupCallPhase.ONGOING
        // Groups are usually listened to on the speaker.
        _speakerOn.value = true
        applySpeaker(true)
        startSpeakingPoller()
    }

    // ───── speaking indicator (from WebRTC audio levels, no network) ─────

    private fun startSpeakingPoller() {
        speakingJob?.cancel()
        speakingJob = scope.launch {
            while (true) {
                delay(500)
                pollSpeaking()
            }
        }
    }

    private fun pollSpeaking() {
        val callId = _session.value?.callId ?: return
        for ((uid, cm) in peers.entries.toList()) {
            cm.pollAudioLevels { local, remote ->
                if (_session.value?.callId == callId) {
                    updateParticipant(uid) {
                        it.copy(speaking = remote > SPEAKING_THRESHOLD && !it.muted)
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (local > SPEAKING_THRESHOLD) lastLocalActiveAt = now
                    val selfSpeaking = !_muted.value && now - lastLocalActiveAt < SPEAKING_HOLD_MS
                    updateParticipant(myUid) { it.copy(speaking = selfSpeaking) }
                }
            }
        }
    }

    // ───── participants list helpers ─────

    private fun selfParticipant() = GroupParticipant(
        uid = myUid,
        username = myUsername.ifBlank { "You" },
        isSelf = true,
        muted = _muted.value,
        link = PeerLink.CONNECTED
    )

    private fun nameOf(uid: String): String =
        _participants.value.firstOrNull { it.uid == uid }?.username ?: ""

    private fun ensureParticipant(uid: String, username: String?) {
        if (uid == myUid) return
        val cur = _participants.value
        if (cur.any { it.uid == uid }) {
            if (!username.isNullOrBlank()) updateParticipant(uid) { it.copy(username = username) }
        } else {
            _participants.value = cur + GroupParticipant(uid, username ?: "")
        }
    }

    private fun updateParticipant(uid: String, change: (GroupParticipant) -> GroupParticipant) {
        _participants.value = _participants.value.map { if (it.uid == uid) change(it) else it }
    }

    // ───── session lifecycle ─────

    private fun beginSession(ctx: Context, s: GroupCallSession, phase: GroupCallPhase) {
        IceServerProvider.prefetch(ctx)
        stopRing()
        lastLocalActiveAt = 0L
        _muted.value = false
        _speakerOn.value = false
        _connectedAt.value = 0L
        _participants.value = emptyList()
        _session.value = s
        _phase.value = phase
    }

    private fun finish() {
        stopRing()
        speakingJob?.cancel()
        speakingJob = null
        peers.values.forEach {
            try { it.release() } catch (_: Exception) { }
        }
        peers.clear()
        resetAudio()
        _connectedAt.value = 0L
        _muted.value = false
        _speakerOn.value = false
        _participants.value = emptyList()
        _session.value = null
        _phase.value = GroupCallPhase.IDLE
    }

    private fun stopRing() {
        ringTimeout?.let { mainHandler.removeCallbacks(it) }
        ringTimeout = null
        ringtone?.stop()
        ringtone = null
    }

    private suspend fun loadIdentity(ctx: Context) {
        myUid = AuthDataStore.getUid(ctx).first() ?: ""
        myUsername = AuthDataStore.getUsername(ctx).first() ?: ""
    }

    private fun applySpeaker(on: Boolean) {
        val ctx = appContext ?: return
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = on
        } catch (_: Exception) {
        }
    }

    private fun resetAudio() {
        val ctx = appContext ?: return
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    private fun errorText(error: String?): String = when (error) {
        "busy" -> "You are already on a call"
        "call_in_progress" -> "A call is already running in this group"
        "full" -> "This call is full"
        "call_ended" -> "This call has ended"
        "Not connected" -> "No connection to the server"
        null -> "Could not start the call"
        else -> error
    }

    private fun toast(msg: String) {
        val ctx = appContext ?: return
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
}
