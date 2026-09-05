package com.muwan.muwanchat.calling

/**
 * Jab "call_offer" event aata hai (koi humein call kar raha hai), uska SDP
 * itna bada hota hai ki navigation route args (URL jaisa) mein safely nahi
 * bhej sakte. Isliye NavGraph ka global listener yahan temporarily store
 * karta hai, CallScreen "incoming" mode mein khulte hi ise turant read
 * karke consume (null) kar leta hai.
 */
object PendingIncomingCall {
    data class Data(
        val callId: String,
        val fromUid: String,
        val fromUsername: String,
        val callType: String,
        val sdp: String
    )

    var data: Data? = null
}
