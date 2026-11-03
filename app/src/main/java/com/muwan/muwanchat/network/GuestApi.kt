package com.muwan.muwanchat.network

// GUEST_P1
// Guest backend (routes/guest.js): challenge -> sign -> session. Server guest ka koi record save nahi karta.
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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

interface GuestApi {
    @GET("guest/challenge")
    suspend fun challenge(): Response<GuestChallengeResponse>

    @POST("guest/session")
    suspend fun session(@Body request: GuestSessionRequest): Response<GuestSessionResponse>
}
