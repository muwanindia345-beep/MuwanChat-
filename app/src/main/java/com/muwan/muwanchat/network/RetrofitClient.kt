package com.muwan.muwanchat.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val CHAT_BACKEND_URL = "https://muwan-chat-backend-production.up.railway.app/"
    private const val MUWANDB_URL = "https://muwandb-server-production.up.railway.app/"

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(CHAT_BACKEND_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    val muwanDbApi: MuwanDbApi by lazy {
        Retrofit.Builder()
            .baseUrl(MUWANDB_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MuwanDbApi::class.java)
    }
}
