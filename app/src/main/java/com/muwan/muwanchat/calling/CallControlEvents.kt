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
}
