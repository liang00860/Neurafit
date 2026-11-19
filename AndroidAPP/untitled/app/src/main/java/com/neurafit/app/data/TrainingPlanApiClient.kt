package com.neurafit.app.data

import com.neurafit.app.BuildConfig
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AiTrainingPlan(
    val title: String,
    val goal: String,
    val difficulty: String,
    val summary: String,
    val startsOn: String,
    val endsOn: String,
    val days: List<AiTrainingPlanDay>,
)

data class AiTrainingPlanDay(
    val date: String,
    val title: String,
    val focus: String,
    val note: String,
    val items: List<AiTrainingPlanItem>,
)

data class AiTrainingPlanItem(
    val name: String,
    val sets: Int,
    val reps: Int?,
    val durationMinutes: Int?,
    val restSeconds: Int,
)

sealed interface TrainingPlanApiResult<out T> {
    data class Success<T>(val value: T) : TrainingPlanApiResult<T>
    data class Error(val message: String) : TrainingPlanApiResult<Nothing>
}

class TrainingPlanApiClient(
    baseUrl: String = BuildConfig.AUTH_BASE_URL,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    suspend fun recommendPlan(token: String): TrainingPlanApiResult<AiTrainingPlan> =
        postPlanRequest(
            path = "/training-plans/recommend",
            token = token,
            body = JSONObject()
                .put("goal", "balanced_strength")
                .put("equipment", "bodyweight")
                .put("days", 7)
                .put("requestedAt", LocalDate.now().toString()),
        ) { root ->
            parsePlan(root.getJSONObject("plan"))
        }

    suspend fun adoptPlan(
        token: String,
        plan: AiTrainingPlan,
    ): TrainingPlanApiResult<AiTrainingPlan> =
        postPlanRequest(
            path = "/training-plans/adopt",
            token = token,
            body = JSONObject().put("plan", plan.toJson()),
        ) { root ->
            parsePlan(root.getJSONObject("plan"))
        }

    private suspend fun <T> postPlanRequest(
        path: String,
        token: String,
        body: JSONObject,
        parse: (JSONObject) -> T,
    ): TrainingPlanApiResult<T> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$normalizedBaseUrl$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    TrainingPlanApiResult.Success(parse(JSONObject(bodyText)))
                } else {
                    TrainingPlanApiResult.Error(parseError(bodyText, response.code))
                }
            }
        }.getOrElse { throwable ->
            TrainingPlanApiResult.Error(throwable.message ?: "AI训练计划服务暂时不可用。")
        }
    }

    private fun parsePlan(root: JSONObject): AiTrainingPlan {
        return AiTrainingPlan(
            title = root.optString("title", "AI训练计划"),
            goal = root.optString("goal", "综合体能"),
            difficulty = root.optString("difficulty", "中等"),
            summary = root.optString("summary", "已根据你的训练状态生成计划。"),
            startsOn = root.optString("startsOn"),
            endsOn = root.optString("endsOn"),
            days = root.optJSONArray("days")?.toPlanDays().orEmpty(),
        )
    }

    private fun JSONArray.toPlanDays(): List<AiTrainingPlanDay> =
        (0 until length()).map { index ->
            val day = getJSONObject(index)
            AiTrainingPlanDay(
                date = day.optString("date"),
                title = day.optString("title", "训练日"),
                focus = day.optString("focus", "综合训练"),
                note = day.optString("note"),
                items = day.optJSONArray("items")?.toPlanItems().orEmpty(),
            )
        }

    private fun JSONArray.toPlanItems(): List<AiTrainingPlanItem> =
        (0 until length()).map { index ->
            val item = getJSONObject(index)
            AiTrainingPlanItem(
                name = item.optString("name", "训练动作"),
                sets = item.optInt("sets", 1).coerceAtLeast(1),
                reps = item.optNullableInt("reps"),
                durationMinutes = item.optNullableInt("durationMinutes"),
                restSeconds = item.optInt("restSeconds", 60).coerceAtLeast(0),
            )
        }

    private fun AiTrainingPlan.toJson(): JSONObject =
        JSONObject()
            .put("title", title)
            .put("goal", goal)
            .put("difficulty", difficulty)
            .put("summary", summary)
            .put("startsOn", startsOn)
            .put("endsOn", endsOn)
            .put("days", JSONArray().apply { days.forEach { put(it.toJson()) } })

    private fun AiTrainingPlanDay.toJson(): JSONObject =
        JSONObject()
            .put("date", date)
            .put("title", title)
            .put("focus", focus)
            .put("note", note)
            .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })

    private fun AiTrainingPlanItem.toJson(): JSONObject =
        JSONObject()
            .put("name", name)
            .put("sets", sets)
            .put("reps", reps)
            .put("durationMinutes", durationMinutes)
            .put("restSeconds", restSeconds)

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun parseError(responseBody: String, responseCode: Int): String =
        runCatching { JSONObject(responseBody).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: String.format(Locale.US, "训练计划接口返回 HTTP %d。", responseCode)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
