package com.muwan.muwanchat.network

import retrofit2.Response
import retrofit2.http.*

data class UserItem(
    val uid: String,
    val username: String,
    val email: String,
    val avatar: String?
)

data class UsersSearchResponse(
    val users: List<UserItem>
)

data class UserResponse(
    val user: UserItem
)

interface UsersApi {
    @GET("users/search")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): Response<UsersSearchResponse>

    @GET("users/{uid}")
    suspend fun getUserByUid(
        @Header("Authorization") token: String,
        @Path("uid") uid: String
    ): Response<UserResponse>

    @POST("users/fcm-token")
    suspend fun updateFcmToken(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Unit>
}
