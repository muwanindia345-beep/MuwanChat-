package com.muwan.muwanchat.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // muwan-auth backend
    private const val AUTH_URL = "https://muwan-chat-backend-production.up.railway.app/"

    // MuwanDB backend
    private const val MUWANDB_URL = "https://muwandb-server.onrender.com/"

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(AUTH_URL)
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
