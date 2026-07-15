package com.muwan.muwanchat.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

data class RegisterRequest(val username: String, val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)
data class VerifyRequest(val email: String, val otp: String)
data class ResendRequest(val email: String)
data class PhoneSendRequest(val phone: String)
data class PhoneVerifyRequest(val phone: String, val otp: String)

data class ChangeUsernameRequest(val username: String)
data class ChangeEmailRequest(val newEmail: String, val currentPassword: String)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

// Generic response for account-settings calls — backend returns
// {success, message, error, username?/email?}; keep it loose here since each
// endpoint only fills in the field relevant to it.
data class AccountActionResponse(
    val success: Boolean?,
    val message: String?,
    val error: String?,
    val username: String?,
    val email: String?
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
    val emailVerified: Boolean,
    val name: String? = null,
    val bio: String? = null,
    val city: String? = null,
    val country: String? = null,
    val gender: String? = null,
    val avatar: String? = null
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

    @GET("auth/me")
    suspend fun me(@Header("Authorization") token: String): Response<AuthResponse>

    @POST("auth/phone/send")
    suspend fun phoneSend(@Body request: PhoneSendRequest): Response<AuthResponse>

    @POST("auth/phone/verify")
    suspend fun phoneVerify(@Body request: PhoneVerifyRequest): Response<AuthResponse>

    @PUT("auth/username")
    suspend fun changeUsername(
        @Header("Authorization") token: String,
        @Body request: ChangeUsernameRequest
    ): Response<AccountActionResponse>

    @PUT("auth/email")
    suspend fun changeEmail(
        @Header("Authorization") token: String,
        @Body request: ChangeEmailRequest
    ): Response<AccountActionResponse>

    @PUT("auth/password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body request: ChangePasswordRequest
    ): Response<AccountActionResponse>

    @DELETE("auth/account")
    suspend fun deleteAccount(@Header("Authorization") token: String): Response<AccountActionResponse>
}
