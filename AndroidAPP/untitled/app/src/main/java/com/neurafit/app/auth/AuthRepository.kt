package com.neurafit.app.auth

import android.content.Context

class AuthRepository(
    context: Context,
    private val apiClient: AuthApiClient = AuthApiClient(),
) {
    private val sessionStore = AuthSessionStore(context.applicationContext)

    suspend fun login(identifier: String, password: String): AuthResult =
        apiClient.login(identifier, password).also(::saveOnSuccess)

    suspend fun register(username: String, email: String, password: String): AuthResult =
        apiClient.register(username, email, password).also(::saveOnSuccess)

    fun hasSession(): Boolean = sessionStore.hasSession()

    fun sessionToken(): String? = sessionStore.token()

    fun userId(): Long? = sessionStore.userId()

    fun logout() {
        sessionStore.clear()
    }

    private fun saveOnSuccess(result: AuthResult) {
        if (result is AuthResult.Success) {
            sessionStore.save(result.session)
        }
    }
}
