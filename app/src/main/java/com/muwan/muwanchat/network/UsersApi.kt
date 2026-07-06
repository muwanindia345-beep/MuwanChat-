package com.muwan.muwanchat.network

import retrofit2.Response
import retrofit2.http.*

data class UserItem(
    val uid: String,
    val username: String,
    val name: String? = null,
    val bio: String? = null,
    val city: String? = null,
    val country: String? = null,
    val gender: String? = null,
    val avatar: String? = null
)

data class UsersSearchResponse(
    val users: List<UserItem>
)

data class UserResponse(
    val user: UserItem
)

data class ProfileUpdateBody(
    val name: String?,
    val bio: String?,
    val city: String?,
    val country: String?,
    val gender: String?,
    val avatar: String?
)

interface UsersApi {
    @GET("users/search")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): Response<UsersSearchResponse>

    @GET("users/suggestions")
    suspend fun getSuggestions(
        @Header("Authorization") token: String
    ): Response<UsersSearchResponse>

    @GET("users/{uid}")
    suspend fun getUserByUid(
        @Header("Authorization") token: String,
        @Path("uid") uid: String
    ): Response<UserResponse>

    @PUT("users/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body body: ProfileUpdateBody
    ): Response<UserResponse>

    @POST("users/fcm-token")
    suspend fun updateFcmToken(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Unit>
}
