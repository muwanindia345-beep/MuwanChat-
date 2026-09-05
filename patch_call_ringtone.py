import os

def create(path, content, label):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[OK] {label}")

def apply(path, old, new, label):
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    n = src.count(old)
    if n != 1:
        raise SystemExit(f"[FAIL] {label} ({path}): found {n} matches (expected 1)\n"
                          f"       Isse pehle 'patch_call_feature_android.py' chala hua hona chahiye.")
    src = src.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print(f"[OK] {label}")

# ============================================================================
# 1) Naya file: CallRingtoneManager.kt
# ============================================================================
create(
    "app/src/main/java/com/muwan/muwanchat/calling/CallRingtoneManager.kt",
'package com.muwan.muwanchat.calling\n\nimport android.content.Context\nimport android.media.AudioAttributes\nimport android.media.AudioManager\nimport android.media.MediaPlayer\nimport android.media.RingtoneManager\nimport android.media.ToneGenerator\nimport android.os.Build\nimport android.os.VibrationEffect\nimport android.os.Vibrator\nimport android.os.VibratorManager\n\n/**\n * Incoming call ke liye phone ki default ringtone (loop mein) + vibration,\n * aur outgoing call ke liye ringback tone (jab tak dusra banda answer na\n * kare). Koi custom mp3 bundle nahi kiya -- Android ka apna RingtoneManager\n * use kiya hai, isliye phone ki khud ki ringtone/silent/vibrate-mode\n * settings automatically respect hoti hain.\n */\nclass CallRingtoneManager(private val context: Context) {\n    private var mediaPlayer: MediaPlayer? = null\n    private var toneGenerator: ToneGenerator? = null\n    private var vibrator: Vibrator? = null\n\n    fun startIncomingRing() {\n        try {\n            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)\n            mediaPlayer = MediaPlayer().apply {\n                setAudioAttributes(\n                    AudioAttributes.Builder()\n                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)\n                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)\n                        .build()\n                )\n                setDataSource(context, uri)\n                isLooping = true\n                prepare()\n                start()\n            }\n        } catch (_: Exception) {}\n\n        try {\n            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {\n                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator\n            } else {\n                @Suppress("DEPRECATION")\n                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator\n            }\n            // 800ms vibrate, 500ms pause, phir repeat (index 0 se loop)\n            val pattern = longArrayOf(0, 800, 500)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {\n                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))\n            } else {\n                @Suppress("DEPRECATION")\n                vibrator?.vibrate(pattern, 0)\n            }\n        } catch (_: Exception) {}\n    }\n\n    /** Caller side: jab tak callee answer na kare, ringback (beep-beep) bajta rahega */\n    fun startOutgoingRingback() {\n        try {\n            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME)\n            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE)\n        } catch (_: Exception) {}\n    }\n\n    fun stop() {\n        try {\n            mediaPlayer?.stop()\n            mediaPlayer?.release()\n        } catch (_: Exception) {}\n        mediaPlayer = null\n\n        try {\n            vibrator?.cancel()\n        } catch (_: Exception) {}\n\n        try {\n            toneGenerator?.stopTone()\n            toneGenerator?.release()\n        } catch (_: Exception) {}\n        toneGenerator = null\n    }\n}\n',
    "create CallRingtoneManager.kt"
)

# ============================================================================
# 2) AndroidManifest.xml -- VIBRATE permission (normal permission, koi
#    runtime request nahi chahiye)
# ============================================================================
apply(
    "app/src/main/AndroidManifest.xml",
'    <uses-permission android:name="android.permission.RECORD_AUDIO" />',
'    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n    <uses-permission android:name="android.permission.VIBRATE" />',
    "AndroidManifest.xml: VIBRATE permission"
)

# ============================================================================
# 3) CallScreen.kt -- ringtoneManager banao aur state ke hisaab se wire karo
# ============================================================================
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/CallScreen.kt",
'    val callManager = remember {\n        CallManager(\n            context = context,\n            onLocalIceCandidate = { candidate ->\n                AppSocketManager.sendIceCandidate(callId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)\n            },\n            onRemoteAudioTrackAdded = { /* audio apne aap play hota hai WebRTC se, UI change ki zaroorat nahi */ },\n            onConnectionFailed = {\n                if (callState != CallState.ENDED) {\n                    callState = CallState.ENDED\n                    navController.popBackStack()\n                }\n            }\n        )\n    }\n\n    // Outgoing call: offer khud banao aur bhejo\n    LaunchedEffect(hasMicPermission) {\n        if (hasMicPermission && !isIncoming && callState == CallState.RINGING_OUTGOING) {\n            callManager.init()\n            callManager.createOffer { sdp ->',
'    val callManager = remember {\n        CallManager(\n            context = context,\n            onLocalIceCandidate = { candidate ->\n                AppSocketManager.sendIceCandidate(callId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)\n            },\n            onRemoteAudioTrackAdded = { /* audio apne aap play hota hai WebRTC se, UI change ki zaroorat nahi */ },\n            onConnectionFailed = {\n                if (callState != CallState.ENDED) {\n                    callState = CallState.ENDED\n                    navController.popBackStack()\n                }\n            }\n        )\n    }\n    val ringtoneManager = remember { com.muwan.muwanchat.calling.CallRingtoneManager(context) }\n\n    // Incoming call pe ringtone+vibration, outgoing pe ringback tone -- jaise\n    // hi state ringing se aage badhe (connecting/ongoing/ended), band ho jaata hai\n    LaunchedEffect(callState) {\n        when (callState) {\n            CallState.RINGING_INCOMING -> ringtoneManager.startIncomingRing()\n            CallState.RINGING_OUTGOING -> ringtoneManager.startOutgoingRingback()\n            else -> ringtoneManager.stop()\n        }\n    }\n\n    // Outgoing call: offer khud banao aur bhejo\n    LaunchedEffect(hasMicPermission) {\n        if (hasMicPermission && !isIncoming && callState == CallState.RINGING_OUTGOING) {\n            callManager.init()\n            callManager.createOffer { sdp ->',
    "CallScreen.kt: wire ringtoneManager"
)

apply(
    "app/src/main/java/com/muwan/muwanchat/screens/CallScreen.kt",
'    // Screen band ho (back button, ya kisi aur wajah se) toh cleanup zaroor ho\n    DisposableEffect(Unit) {\n        onDispose {\n            if (callState != CallState.ENDED) {\n                AppSocketManager.sendCallEnd(callId)\n            }\n            callManager.release()\n        }\n    }',
'    // Screen band ho (back button, ya kisi aur wajah se) toh cleanup zaroor ho\n    DisposableEffect(Unit) {\n        onDispose {\n            if (callState != CallState.ENDED) {\n                AppSocketManager.sendCallEnd(callId)\n            }\n            callManager.release()\n            ringtoneManager.stop()\n        }\n    }',
    "CallScreen.kt: stop ringtone on dispose"
)

print()
print("[DONE] Incoming ringtone+vibration aur outgoing ringback tone add ho gaye")
