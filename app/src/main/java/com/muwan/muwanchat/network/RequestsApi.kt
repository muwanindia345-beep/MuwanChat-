package com.muwan.muwanchat.network

import retrofit2.Response
import retrofit2.http.*

data class ChatRequest(
    val id: String,
    val sender_uid: String,
    val receiver_uid: String,
    val status: String,
    val created_at: String,
    val username: String = "Unknown",
    val avatar: String? = null
)

data class RequestsResponse(
    val requests: List<ChatRequest> = emptyList()
)

data class SendRequestBody(
    val receiver_uid: String
)

data class SendRequestResponse(
    val success: Boolean,
    val id: String?,
    val error: String?
)

data class SimpleResponse(
    val success: Boolean
)

interface RequestsApi {
    @POST("requests/send")
    suspend fun sendRequest(
        @Header("Authorization") token: String,
        @Body body: SendRequestBody
    ): Response<SendRequestResponse>

    @GET("requests/incoming")
    suspend fun getIncoming(
        @Header("Authorization") token: String
    ): Response<RequestsResponse>

    @GET("requests/sent")
    suspend fun getSent(
        @Header("Authorization") token: String
    ): Response<RequestsResponse>

    @PUT("requests/{id}/accept")
    suspend fun acceptRequest(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SimpleResponse>

    @PUT("requests/{id}/reject")
    suspend fun rejectRequest(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SimpleResponse>

    @GET("requests/accepted")
    suspend fun getAccepted(
        @Header("Authorization") token: String
    ): Response<UsersSearchResponse>

    // Permanent unfriend -- "Accepted Users" settings screen se call hota hai.
    // Backend accepted request record delete karta hai + shared room data saaf
    // karta hai, aur otherUid ko "connection_removed" socket event emit karta hai.
    @DELETE("requests/accepted/{otherUid}")
    suspend fun removeConnection(
        @Header("Authorization") token: String,
        @Path("otherUid") otherUid: String
    ): Response<SimpleResponse>
}
