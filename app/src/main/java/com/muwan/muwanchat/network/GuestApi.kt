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
}
