package com.muwan.muwanchat.network

import retrofit2.Response
import retrofit2.http.*

// Backend: routes/status.js. Saari fields ka default hai taaki Gson kisi bhi
// missing field par crash na kare (like_count / liked / link_title backend
// patch ke baad aayenge -- tab tak default 0 / false / null rahenge).
data class StatusItemDto(
    val id: String = "",
    val type: String = "text",          // text | image | video | document | link
    val text: String = "",
    val bg_color: String? = null,
    val media_url: String? = null,
    val thumbnail_url: String? = null,
    val file_name: String? = null,
    val mime_type: String? = null,
    val link_url: String? = null,
    val link_title: String? = null,
    val created_at: String = "",        // ISO-8601, UTC
    val seen: Boolean = false,
    val view_count: Int = 0,
    val like_count: Int = 0,
    val liked: Boolean = false
)

data class StatusUserDto(
    val uid: String = "",
    val username: String = "Unknown",
    val avatar: String? = null,         // base64 (AvatarView yahi leta hai)
    val statuses: List<StatusItemDto> = emptyList(),
    val all_seen: Boolean = false,
    val latest_at: String = ""
)

data class StatusFeedResponse(
    val mine: List<StatusItemDto> = emptyList(),
    val friends: List<StatusUserDto> = emptyList()
)

data class CreateStatusBody(
    val type: String,
    val text: String? = null,
    val bg_color: String? = null,
    val media_url: String? = null,
    val thumbnail_url: String? = null,
    val file_name: String? = null,
    val mime_type: String? = null,
    val link_url: String? = null
)

data class CreateStatusResponse(
    val success: Boolean = false,
    val status: StatusItemDto? = null,
    val error: String? = null
)

data class StatusSimpleResponse(
    val success: Boolean = false,
    val error: String? = null
)

interface StatusApi {
    @GET("status/feed")
    suspend fun getFeed(
        @Header("Authorization") token: String
    ): Response<StatusFeedResponse>

    @POST("status")
    suspend fun create(
        @Header("Authorization") token: String,
        @Body body: CreateStatusBody
    ): Response<CreateStatusResponse>

    @POST("status/{id}/view")
    suspend fun markViewed(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<StatusSimpleResponse>

    @DELETE("status/{id}")
    suspend fun delete(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<StatusSimpleResponse>

    // Likes: backend endpoints abhi nahi hain (backend patch Step B). Tab tak 404.
    @POST("status/{id}/like")
    suspend fun like(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<StatusSimpleResponse>

    @DELETE("status/{id}/like")
    suspend fun unlike(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<StatusSimpleResponse>
}
