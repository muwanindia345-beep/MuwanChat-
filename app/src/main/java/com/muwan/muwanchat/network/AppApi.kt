package com.muwan.muwanchat.network

import retrofit2.http.GET
import retrofit2.http.Header

data class AppVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val apkUrl: String?,
    val releaseDate: String?
)

data class IceServerDto(val urls: Any?, val username: String? = null, val credential: String? = null)
data class IceServersResponse(val iceServers: List<IceServerDto>, val turn: Boolean = false)

interface AppApi {
    @GET("ice")
    suspend fun getIceServers(@Header("Authorization") token: String): IceServersResponse

    @GET("app/version")
    suspend fun getVersion(): AppVersionInfo
}
