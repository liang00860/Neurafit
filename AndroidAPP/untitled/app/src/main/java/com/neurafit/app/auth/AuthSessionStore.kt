package com.neurafit.app.auth

import android.content.Context

class AuthSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    fun save(session: AuthSession) {
        preferences.edit()
            .putString(KEY_TOKEN, session.token)
            .putLong(KEY_USER_ID, session.user.id)
            .putString(KEY_USERNAME, session.user.username)
            .putString(KEY_EMAIL, session.user.email)
            .apply()
    }

    fun hasSession(): Boolean =
        !preferences.getString(KEY_TOKEN, null).isNullOrBlank()

    fun token(): String? =
        preferences.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun userId(): Long? =
        preferences.getLong(KEY_USER_ID, 0L).takeIf { it > 0L }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
    }
}
