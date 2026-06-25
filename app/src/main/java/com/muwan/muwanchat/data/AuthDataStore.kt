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

    private val USERNAME_KEY  = stringPreferencesKey("username")
    private val EMAIL_KEY     = stringPreferencesKey("email")
    private val ANON_KEY      = stringPreferencesKey("anon_key")
    private val SECRET_KEY    = stringPreferencesKey("secret_key")
    private val DB_NAME_KEY   = stringPreferencesKey("db_name")
    private val LOGIN_TYPE_KEY = stringPreferencesKey("login_type") // "email" | "phone" | "google"

    suspend fun saveAuth(
        context: Context,
        username: String,
        email: String,
        anonKey: String,
        secretKey: String,
        dbName: String,
        loginType: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME_KEY]   = username
            prefs[EMAIL_KEY]      = email
            prefs[ANON_KEY]       = anonKey
            prefs[SECRET_KEY]     = secretKey
            prefs[DB_NAME_KEY]    = dbName
            prefs[LOGIN_TYPE_KEY] = loginType
        }
    }

    fun getUsername(context: Context): Flow<String?> =
        context.dataStore.data.map { it[USERNAME_KEY] }

    fun getEmail(context: Context): Flow<String?> =
        context.dataStore.data.map { it[EMAIL_KEY] }

    fun getAnonKey(context: Context): Flow<String?> =
        context.dataStore.data.map { it[ANON_KEY] }

    fun getSecretKey(context: Context): Flow<String?> =
        context.dataStore.data.map { it[SECRET_KEY] }

    fun getDbName(context: Context): Flow<String?> =
        context.dataStore.data.map { it[DB_NAME_KEY] }

    fun getLoginType(context: Context): Flow<String?> =
        context.dataStore.data.map { it[LOGIN_TYPE_KEY] }

    fun isLoggedIn(context: Context): Flow<Boolean> =
        context.dataStore.data.map {
            !it[ANON_KEY].isNullOrEmpty()
        }

    suspend fun clearAuth(context: Context) {
        context.dataStore.edit { it.clear() }
    }
}
