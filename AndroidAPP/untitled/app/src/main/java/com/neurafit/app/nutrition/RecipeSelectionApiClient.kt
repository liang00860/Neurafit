package com.neurafit.app.nutrition

import android.content.Context
import com.neurafit.app.BuildConfig
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RecipeSelectionApiClient(
    context: Context,
    baseUrl: String = BuildConfig.AUTH_BASE_URL,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : RecipeSelectionUploader {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    override suspend fun upload(record: SelectedRecipeRecord, token: String): RecipeSelectionResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(selectedRecipesUrl())
                    .header("Authorization", "Bearer $token")
                    .post(record.toJson().toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        RecipeSelectionResult.Success(parseRecordId(bodyText))
                    } else {
                        RecipeSelectionResult.Error(parseError(bodyText, response.code))
                    }
                }
            }.getOrElse { throwable ->
                RecipeSelectionResult.Error(throwable.message ?: "食谱保存失败，请稍后重试。")
            }
        }

    private fun selectedRecipesUrl(): String {
        return if (normalizedBaseUrl.endsWith("/api")) {
            "$normalizedBaseUrl/selected-recipes"
        } else {
            "$normalizedBaseUrl/api/selected-recipes"
        }
    }

    private fun SelectedRecipeRecord.toJson(): JSONObject =
        JSONObject()
            .put("recipeId", recipeId)
            .put("title", title)
            .put("imageUrl", imageUrl)
            .put("prepTimeMinutes", prepTimeMinutes)
            .put("caloriesKcal", caloriesKcal)
            .put("carbsGrams", carbsGrams.formatDecimal())
            .put("proteinGrams", proteinGrams.formatDecimal())
            .put("fiberGrams", fiberGrams.formatDecimal())
            .put("ingredients", ingredients)
            .put("steps", steps)

    private fun parseRecordId(responseBody: String): Long? =
        runCatching {
            val root = JSONObject(responseBody)
            val recipe = root.optJSONObject("recipe")
            if (recipe != null && recipe.has("id")) recipe.getLong("id") else null
        }.getOrNull()

    private fun parseError(responseBody: String, responseCode: Int): String =
        runCatching { JSONObject(responseBody).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: String.format(Locale.US, "食谱接口返回 HTTP %d。", responseCode)

    private fun Float.formatDecimal(): String =
        String.format(Locale.US, "%.2f", this)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
