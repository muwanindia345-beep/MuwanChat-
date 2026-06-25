package com.muwan.muwanchat.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// ─── MuwanDB Request Models ───────────────────────────────────────
data class MuwanRegisterRequest(
    val username: String,
    val password: String,
    val dbName: String
)

data class MuwanLoginRequest(
    val username: String,
    val password: String
)

// ─── MuwanDB Response Models ──────────────────────────────────────
data class MuwanRegisterResponse(
    val message: String?,
    val username: String?,
    val dbName: String?,
    val anonKey: String?,
    val secretKey: String?,
    val error: String?
)

data class MuwanLoginResponse(
    val message: String?,
    val username: String?,
    val dbName: String?,
    val anonKey: String?,
    val secretKey: String?,
    val error: String?
)

// ─── MuwanDB API Interface ────────────────────────────────────────
interface MuwanDbApi {

    @POST("auth/register")
    suspend fun register(
        @Body request: MuwanRegisterRequest
    ): Response<MuwanRegisterResponse>

    @POST("auth/login")
    suspend fun login(
        @Body request: MuwanLoginRequest
    ): Response<MuwanLoginResponse>
}
