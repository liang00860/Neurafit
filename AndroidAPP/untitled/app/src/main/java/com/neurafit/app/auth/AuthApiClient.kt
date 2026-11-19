package com.neurafit.app.auth

import com.neurafit.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AuthApiClient(
    baseUrl: String = BuildConfig.AUTH_BASE_URL,
) {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    suspend fun login(identifier: String, password: String): AuthResult =
        post(
            path = "/login",
            body = JSONObject()
                .put("identifier", identifier)
                .put("password", password),
        )

    suspend fun register(username: String, email: String, password: String): AuthResult =
        post(
            path = "/register",
            body = JSONObject()
                .put("username", username)
                .put("email", email)
                .put("password", password),
        )

    private suspend fun post(path: String, body: JSONObject): AuthResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL("$normalizedBaseUrl$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }

                connection.outputStream.use { stream ->
                    stream.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()

                if (responseCode in 200..299) {
                    parseSuccess(responseBody)
                } else {
                    AuthResult.Error(parseError(responseBody, responseCode))
                }
            }.getOrElse { throwable ->
                AuthResult.Error(throwable.message ?: "Network request failed")
            }
        }

    private fun parseSuccess(responseBody: String): AuthResult {
        val root = JSONObject(responseBody)
        val userJson = root.getJSONObject("user")
        return AuthResult.Success(
            AuthSession(
                token = root.getString("token"),
                user = AuthUser(
                    id = userJson.getLong("id"),
                    username = userJson.getString("username"),
                    email = userJson.optNullableString("email"),
                ),
            ),
        )
    }

    private fun parseError(responseBody: String, responseCode: Int): String =
        runCatching {
            JSONObject(responseBody).optString("error")
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Login service returned HTTP $responseCode"

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null
}
