package com.muwan.muwanchat.network

// GUEST_P1
// Guest backend (routes/guest.js): challenge -> sign -> session. Server guest ka koi record save nahi karta.
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header  // GUEST_P2B1
import retrofit2.http.POST

data class GuestChallengeResponse(
    val challenge: String?,
    val expiresInSec: Int?,
    val error: String?
)

// publicKey = Ed25519 raw 32 bytes (base64), signature = Ed25519(UTF8("muwan-guest-v1:" + challenge)) (base64)
data class GuestSessionRequest(
    val publicKey: String,
    val challenge: String,
    val signature: String
)

data class GuestSessionResponse(
    val success: Boolean?,
    val guestId: String?,
    val token: String?,
    val ttlDays: Int?,
    val error: String?
)

data class GuestInviteRequest(
    val hash: String,
    val uses: Int,
    val ttlHours: Int
)

data class GuestInviteResponse(
    val success: Boolean?,
    val uses: Int?,
    val expiresAt: String?,
    val error: String?
)

data class GuestRedeemRequest(
    val to: String,
    val secret: String
)

// GUEST_P2C -- HTTP relay (socket down hone par fallback)
data class GuestEnvelopeOut(
    val to: String,
    val mid: String,
    val kind: String,
    val payload: String
)

data class GuestRelayRequest(
    val envelopes: List<GuestEnvelopeOut>
)

data class GuestRelayResult(
    val mid: String?,
    val to: String?,
    val status: String?
)

data class GuestRelayResponse(
    val success: Boolean?,
    val results: List<GuestRelayResult>?,
    val error: String?
)

// GUEST_P2D -- short invite link (backend guest_backend_v4)
data class GuestLinkRequest(
    val publicKey: String,
    val name: String,
    val country: String,
    val ttlHours: Int,
    val code: String? = null
)

data class GuestLinkResponse(
    val success: Boolean?,
    val code: String?,
    val expiresAt: String?,
    val g: String?,
    val k: String?,
    val n: String?,
    val c: String?,
    val error: String?
)

// GUEST_P2F -- push token + HTTP mailbox (push se jaagne par)
data class GuestPushTokenRequest(
    val token: String
)

data class GuestAckRequest(
    val ids: List<String>
)

interface GuestApi {
    @GET("guest/challenge")
    suspend fun challenge(): Response<GuestChallengeResponse>

    @POST("guest/session")
    suspend fun session(@Body request: GuestSessionRequest): Response<GuestSessionResponse>

    // GUEST_P2B1: invite = secret ka SHA-256 hash (secret server ko kabhi nahi jaata)
    @POST("guest/invite")
    suspend fun createInvite(
        @Header("Authorization") auth: String,
        @Body request: GuestInviteRequest
    ): Response<GuestInviteResponse>

    // GUEST_P2B2: invite redeem -- success par dono taraf allow ban jaata hai
    @POST("guest/invite/redeem")
    suspend fun redeemInvite(
        @Header("Authorization") auth: String,
        @Body request: GuestRedeemRequest
    ): Response<GuestInviteResponse>

    // GUEST_P2C
    @POST("guest/relay")
    suspend fun relay(
        @Header("Authorization") auth: String,
        @Body request: GuestRelayRequest
    ): Response<GuestRelayResponse>

    // GUEST_P2D
    @POST("guest/link")
    suspend fun createLink(
        @Header("Authorization") auth: String,
        @Body request: GuestLinkRequest
    ): Response<GuestLinkResponse>

    @GET("guest/link/{code}")
    suspend fun getLink(@retrofit2.http.Path("code") code: String): Response<GuestLinkResponse>

    // GUEST_P2F
    @POST("guest/push-token")
    suspend fun registerPush(
        @Header("Authorization") auth: String,
        @Body request: GuestPushTokenRequest
    ): Response<okhttp3.ResponseBody>

    @GET("guest/mailbox")
    suspend fun mailbox(
        @Header("Authorization") auth: String,
        @retrofit2.http.Query("after") after: String?,
        @retrofit2.http.Query("limit") limit: Int
    ): Response<okhttp3.ResponseBody>

    @POST("guest/mailbox/ack")
    suspend fun ackMailbox(
        @Header("Authorization") auth: String,
        @Body request: GuestAckRequest
    ): Response<okhttp3.ResponseBody>
}
