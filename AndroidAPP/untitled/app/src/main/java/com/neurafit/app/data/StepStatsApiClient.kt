package com.neurafit.app.data

import com.neurafit.app.BuildConfig
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

sealed interface StepStatsApiResult<out T> {
    data class Success<T>(val value: T) : StepStatsApiResult<T>
    data class Error(val message: String) : StepStatsApiResult<Nothing>
}

class StepStatsApiClient(
    baseUrl: String = BuildConfig.AUTH_BASE_URL,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    suspend fun syncStepRecords(
        records: List<DailyStepRecord>,
        token: String,
    ): StepStatsApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put(
                    "records",
                    JSONArray().apply {
                        records.forEach { record ->
                            put(
                                JSONObject()
                                    .put("date", record.date)
                                    .put("stepCount", record.stepCount)
                                    .put("source", record.source),
                            )
                        }
                    },
                )

            val request = Request.Builder()
                .url("$normalizedBaseUrl/step-records/sync")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    StepStatsApiResult.Success(Unit)
                } else {
                    StepStatsApiResult.Error(parseError(bodyText, response.code))
                }
            }
        }.getOrElse { throwable ->
            StepStatsApiResult.Error(throwable.message ?: "步数同步失败，请稍后重试。")
        }
    }

    private fun parseError(responseBody: String, responseCode: Int): String =
        runCatching { JSONObject(responseBody).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: String.format(Locale.US, "步数接口返回 HTTP %d。", responseCode)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
