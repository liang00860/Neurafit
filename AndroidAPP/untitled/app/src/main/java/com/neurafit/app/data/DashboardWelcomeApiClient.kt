package com.neurafit.app.data

import com.neurafit.app.BuildConfig
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class DashboardWelcomeAdvice(
    val aiAdvice: String,
)

sealed interface DashboardWelcomeApiResult<out T> {
    data class Success<T>(val value: T) : DashboardWelcomeApiResult<T>
    data class Error(val message: String) : DashboardWelcomeApiResult<Nothing>
}

class DashboardWelcomeApiClient(
    baseUrl: String = BuildConfig.AUTH_BASE_URL,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    suspend fun fetchAdvice(token: String): DashboardWelcomeApiResult<DashboardWelcomeAdvice> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$normalizedBaseUrl/dashboard/welcome-advice")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use DashboardWelcomeApiResult.Error(parseError(bodyText, response.code))
                    }

                    val root = JSONObject(bodyText)
                    DashboardWelcomeApiResult.Success(
                        DashboardWelcomeAdvice(
                            aiAdvice = root.optString("aiAdvice", ""),
                        ),
                    )
                }
            }.getOrElse { throwable ->
                DashboardWelcomeApiResult.Error(
                    throwable.message ?: "首页建议加载失败，请稍后重试。",
                )
            }
        }

    private fun parseError(responseBody: String, responseCode: Int): String =
        runCatching { JSONObject(responseBody).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: String.format(Locale.US, "首页建议接口返回 HTTP %d。", responseCode)
}

