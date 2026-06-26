package com.muwan.muwanchat.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val CHAT_BACKEND_URL = "https://muwan-chat-backend-production.up.railway.app/"

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(CHAT_BACKEND_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    val chatApi: ChatApi by lazy {
        Retrofit.Builder()
            .baseUrl(CHAT_BACKEND_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApi::class.java)
    }

    val usersApi: UsersApi by lazy {
        Retrofit.Builder()
            .baseUrl(CHAT_BACKEND_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UsersApi::class.java)
    }

    val requestsApi: RequestsApi by lazy {
        Retrofit.Builder()
            .baseUrl(CHAT_BACKEND_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RequestsApi::class.java)
    }
}
