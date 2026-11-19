package com.neurafit.app.data

import com.neurafit.app.BuildConfig
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class TrainingDurationSessionPayload(
    val clientTrainingId: Long,
    val actionName: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val repCount: Int,
    val averageScore: Int,
)

data class TrainingDurationSummary(
    val totalDurationMs: Long,
    val sessionCount: Int,
    val deltaFromLastWeekMs: Long,
    val totalCaloriesKcal: Double,
    val longestSessionDurationMs: Long,
)

sealed interface TrainingDurationApiResult<out T> {
    data class Success<T>(val value: T) : TrainingDurationApiResult<T>
    data class Error(val message: String) : TrainingDurationApiResult<Nothing>
}

class TrainingDurationApiClient(
    baseUrl: String = BuildConfig.AUTH_BASE_URL,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    suspend fun syncSessions(
        sessions: List<TrainingDurationSessionPayload>,
        token: String,
    ): TrainingDurationApiResult<Unit> = withContext(Dispatchers.IO) {
        if (sessions.isEmpty()) {
            return@withContext TrainingDurationApiResult.Success(Unit)
        }

        runCatching {
            val body = JSONObject()
                .put(
                    "sessions",
                    JSONArray().apply {
                        sessions.forEach { session ->
                            put(
                                JSONObject()
                                    .put("clientTrainingId", session.clientTrainingId)
                                    .put("actionName", session.actionName)
                                    .put("startedAtMs", session.startedAtMs)
                                    .put("durationMs", session.durationMs)
                                    .put("repCount", session.repCount)
                                    .put("averageScore", session.averageScore),
                            )
                        }
                    },
                )

            val request = Request.Builder()
                .url("$normalizedBaseUrl/training-sessions/sync")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    TrainingDurationApiResult.Success(Unit)
                } else {
                    TrainingDurationApiResult.Error(parseError(bodyText, response.code))
                }
            }
        }.getOrElse { throwable ->
            TrainingDurationApiResult.Error(
                throwable.message ?: "训练时长同步失败，请稍后重试。",
            )
        }
    }

    suspend fun fetchSummary(token: String): TrainingDurationApiResult<TrainingDurationSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$normalizedBaseUrl/training-stats/summary")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use TrainingDurationApiResult.Error(parseError(bodyText, response.code))
                    }

                    val root = JSONObject(bodyText)
                    TrainingDurationApiResult.Success(
                        TrainingDurationSummary(
                            totalDurationMs = root.optLong("totalDurationMs", 0L),
                            sessionCount = root.optInt("sessionCount", 0),
                            deltaFromLastWeekMs = root.optLong("deltaFromLastWeekMs", 0L),
                            totalCaloriesKcal = root.optDouble("totalCaloriesKcal", 0.0),
                            longestSessionDurationMs = root.optLong("longestSessionDurationMs", 0L),
                        ),
                    )
                }
            }.getOrElse { throwable ->
                TrainingDurationApiResult.Error(
                    throwable.message ?: "训练时长统计加载失败，请稍后重试。",
                )
            }
        }

    private fun parseError(responseBody: String, responseCode: Int): String =
        runCatching { JSONObject(responseBody).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: String.format(Locale.US, "训练统计接口返回 HTTP %d。", responseCode)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
