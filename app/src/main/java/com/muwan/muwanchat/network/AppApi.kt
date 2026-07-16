package com.muwan.muwanchat.network

import retrofit2.http.GET

data class AppVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val apkUrl: String?,
    val releaseDate: String?
)

interface AppApi {
    @GET("app/version")
    suspend fun getVersion(): AppVersionInfo
}
