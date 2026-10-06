package com.muwan.muwanchat.calling

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Jab Decline notification (CallForegroundService) se dabaya jaata hai, uss
 * waqt CallScreen bhi already open ho sakti hai (kyunki NavGraph call_offer
 * aate hi turant navigate kar deta hai). CallForegroundService khud hi
 * `AppSocketManager.sendCallReject()` bhula deta hai -- CallScreen ko sirf
 * itna pata chalna chahiye ki "ye call notification se decline ho chuki hai,
 * ab khud dobara reject mat bhejo, bas UI band karo aur ringtone roko."
 *
 * Isi asymmetry ke liye ye chhota shared flow hai -- na ki CallScreen aur
 * service dono independently reject bhejein (double `call_reject` emit se
 * bachne ke liye).
 */
object CallControlEvents {
    private val _declinedFromNotification = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val declinedFromNotification = _declinedFromNotification.asSharedFlow()

    fun notifyDeclinedFromNotification(callId: String) {
        _declinedFromNotification.tryEmit(callId)
    }

    // CALL_PUSH_PATCH: notification ke "Answer" ka signal. CallScreen abhi khuli na ho
    // (app killed tha) to callId yahan rukta hai, aur khulte hi CallScreen khud accept kar leti hai.
    @Volatile var answerRequestedCallId: String? = null
    private val _answeredFromNotification = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val answeredFromNotification = _answeredFromNotification.asSharedFlow()

    fun notifyAnsweredFromNotification(callId: String) {
        answerRequestedCallId = callId
        _answeredFromNotification.tryEmit(callId)
    }

    // Notification se decline ki hui calls -- offer replay aaye to NavGraph dobara screen na khole
    val declinedCallIds: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Jis incoming call ki CallScreen abhi khuli hai (push dobara ring na kare)
    @Volatile var screenCallId: String? = null

    // Notification ka "Answer" seedha MainActivity kholta hai (Android 12+ service se activity start block karta hai)
    const val ACTION_ANSWER_FROM_NOTIFICATION = "com.muwan.muwanchat.calling.ANSWER_FROM_NOTIFICATION"
}
