package com.muwan.muwanchat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

object AuthDataStore {
    private val TOKEN_KEY = stringPreferencesKey("jwt_token")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val EMAIL_KEY = stringPreferencesKey("email")
    private val UID_KEY = stringPreferencesKey("uid")

    suspend fun saveAuth(context: Context, token: String, username: String, email: String, uid: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USERNAME_KEY] = username
            prefs[EMAIL_KEY] = email
            prefs[UID_KEY] = uid
        }
    }

    fun getToken(context: Context): Flow<String?> =
        context.dataStore.data.map { it[TOKEN_KEY] }

    fun getUsername(context: Context): Flow<String?> =
        context.dataStore.data.map { it[USERNAME_KEY] }

    fun getEmail(context: Context): Flow<String?> =
        context.dataStore.data.map { it[EMAIL_KEY] }

    suspend fun clearAuth(context: Context) {
        context.dataStore.edit { it.clear() }
    }
}
