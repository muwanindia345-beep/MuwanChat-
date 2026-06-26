package com.muwan.muwanchat.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class VerifyRequest(
    val email: String,
    val otp: String
)

data class ResendRequest(
    val email: String
)

data class PhoneSendRequest(
    val phone: String
)

data class PhoneVerifyRequest(
    val phone: String,
    val otp: String
)

data class AuthResponse(
    val success: Boolean?,
    val token: String?,
    val message: String?,
    val error: String?,
    val uid: String?,
    val needsVerification: Boolean?,
    val user: UserData?
)

data class UserData(
    val uid: String,
    val username: String,
    val email: String,
    val emailVerified: Boolean
)

interface AuthApi {
    @POST("auth/email/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/email/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/email/verify")
    suspend fun verify(@Body request: VerifyRequest): Response<AuthResponse>

    @POST("auth/email/resend")
    suspend fun resend(@Body request: ResendRequest): Response<AuthResponse>

    @POST("auth/phone/send")
    @GET("auth/me")
    suspend fun me(@Header("Authorization") token: String): Response<AuthResponse>

    @POST("auth/phone/send")
    suspend fun phoneSend(@Body request: PhoneSendRequest): Response<AuthResponse>

    @POST("auth/phone/verify")
    suspend fun phoneVerify(@Body request: PhoneVerifyRequest): Response<AuthResponse>
}
