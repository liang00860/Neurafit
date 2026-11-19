package com.neurafit.app.data

import android.content.Context
import com.neurafit.app.auth.AuthSessionStore

class DashboardWelcomeRepository(
    context: Context,
    private val sessionStore: AuthSessionStore = AuthSessionStore(context.applicationContext),
    private val apiClient: DashboardWelcomeApiClient = DashboardWelcomeApiClient(),
) {
    suspend fun fetchAdvice(fallbackAdvice: String): String {
        val token = sessionStore.token()
        if (token.isNullOrBlank()) {
            return fallbackAdvice
        }

        return when (val result = apiClient.fetchAdvice(token)) {
            is DashboardWelcomeApiResult.Success -> result.value.aiAdvice.ifBlank { fallbackAdvice }
            is DashboardWelcomeApiResult.Error -> fallbackAdvice
        }
    }
}

