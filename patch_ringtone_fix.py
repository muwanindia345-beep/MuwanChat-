import os

def create(path, content, label):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[OK] {label}")

# ============================================================================
# CallRingtoneManager.kt -- raw MediaPlayer se system ringtone URI play karna
# kaafi OEM devices (MIUI/EMUI) pe silently fail ho jaata hai (vibration
# chalta rahta hai kyunki uska code path bilkul alag hai). Ab Android ka
# apna `Ringtone` class use kiya hai (RingtoneManager.getRingtone) jo iske
# liye specifically bana hai, saath mein ek chhota watchdog jo har 500ms
# check karke true looping guarantee karta hai (Ringtone.isLooping sirf
# API 28+ pe kaam karta hai, watchdog sabhi versions pe consistent rakhta hai).
# ============================================================================
create(
    "app/src/main/java/com/muwan/muwanchat/calling/CallRingtoneManager.kt",
'''package com.muwan.muwanchat.calling

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Incoming call ke liye phone ki default ringtone (loop mein) + vibration,
 * aur outgoing call ke liye ringback tone (jab tak dusra banda answer na
 * kare). Koi custom mp3 bundle nahi kiya -- Android ka apna RingtoneManager
 * use kiya hai, isliye phone ki khud ki ringtone/silent/vibrate-mode
 * settings automatically respect hoti hain.
 *
 * NOTE: raw MediaPlayer se system ringtone URI (content://settings/system/
 * ringtone) play karna kaafi OEM devices (MIUI/EMUI) pe silently fail ho
 * jaata hai -- Android ka apna `Ringtone` class (RingtoneManager.getRingtone)
 * use karna zyada reliable hai, isiliye yahan usi se rewrite kiya.
 */
class CallRingtoneManager(private val context: Context) {
    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var watchdogJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startIncomingRing() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
            // `Ringtone` class ke paas completion-listener nahi hota, aur
            // isLooping sirf API 28+ pe kaam karta hai -- isliye yeh chhota
            // watchdog har 500ms check karta hai ki ring ruk toh nahi gayi
            // (khatam ho gayi ya kisi wajah se stop ho gayi), aur agar hum
            // khud stop() nahi bole toh usse dobara start kar deta hai.
            // Isse hi looping guaranteed hoti hai, har Android version pe.
            watchdogJob = scope.launch {
                while (isActive) {
                    delay(500)
                    if (ringtone?.isPlaying == false) {
                        try { ringtone?.play() } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            // 800ms vibrate, 500ms pause, phir repeat (index 0 se loop)
            val pattern = longArrayOf(0, 800, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    /** Caller side: jab tak callee answer na kare, ringback (beep-beep) bajta rahega */
    fun startOutgoingRingback() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME)
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE)
        } catch (_: Exception) {}
    }

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null

        try {
            ringtone?.stop()
        } catch (_: Exception) {}
        ringtone = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}

        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
    }
}
''',
    "CallRingtoneManager.kt: fix silent ringtone (MediaPlayer -> Ringtone class)"
)

print()
print("[DONE] Incoming call ringtone fix applied")
