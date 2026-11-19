package com.neurafit.app.auth

data class AuthUser(
    val id: Long,
    val username: String,
    val email: String?,
)

data class AuthSession(
    val token: String,
    val user: AuthUser,
)

sealed interface AuthResult {
    data class Success(val session: AuthSession) : AuthResult
    data class Error(val message: String) : AuthResult
}
