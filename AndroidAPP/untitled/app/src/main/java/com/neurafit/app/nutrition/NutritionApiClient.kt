package com.neurafit.app.nutrition

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.neurafit.app.BuildConfig
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject

class NutritionApiClient(
    context: Context,
    baseUrl: String = BuildConfig.AUTH_BASE_URL,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : NutritionUploader {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    override suspend fun upload(record: NutritionScanRecord, token: String): NutritionSaveResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(nutritionRecordsUrl())
                    .header("Authorization", "Bearer $token")
                    .post(record.toJsonRequestBody())
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val recordId = parseRecordId(bodyText)
                        NutritionSaveResult.Success(recordId)
                    } else {
                        NutritionSaveResult.Error(parseError(bodyText, response.code))
                    }
                }
            }.getOrElse { throwable ->
                NutritionSaveResult.Error(throwable.message ?: "营养记录保存失败，请稍后重试。")
            }
        }

    private fun nutritionRecordsUrl(): String {
        return if (normalizedBaseUrl.endsWith("/api")) {
            "$normalizedBaseUrl/nutrition-records"
        } else {
            "$normalizedBaseUrl/api/nutrition-records"
        }
    }

    private fun NutritionScanRecord.toJsonRequestBody(): RequestBody {
        val payload = JSONObject()
            .put("scanId", scanId)
            .put("imageUri", imageUri)
            .put("foodName", foodName)
            .put("label", foodLabel)
            .put("confidence", confidence.formatDecimal())
            .put("servingGrams", servingGrams)
            .put("caloriesKcal", caloriesKcal)
            .put("proteinGrams", proteinGrams.formatDecimal())
            .put("dietaryFiberGrams", dietaryFiberGrams.formatDecimal())
            .put("carbsGrams", carbsGrams.formatDecimal())
            .put("fatGrams", fatGrams.formatDecimal())
            .put("analysisSummary", analysisSummary)
            .put("dataSource", dataSource)
            .put("itemSummary", itemSummary)
        return payload.toString().toRequestBody(JSON_MEDIA_TYPE)
    }

    private fun imageRequestBody(uri: Uri, mimeType: String): RequestBody =
        object : RequestBody() {
            override fun contentType(): MediaType = mimeType.toMediaType()

            override fun contentLength(): Long = querySize(uri)

            override fun writeTo(sink: BufferedSink) {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IOException("无法读取食物照片。")
                inputStream.use { input ->
                    input.copyTo(sink.outputStream())
                }
            }
        }

    private fun resolveMimeType(uri: Uri): String =
        contentResolver.getType(uri)?.takeIf { it in SUPPORTED_IMAGE_TYPES } ?: "image/jpeg"

    private fun resolveFileName(uri: Uri): String {
        val queriedName = query(uri) { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
        return queriedName?.takeIf { it.isNotBlank() } ?: "food-photo.jpg"
    }

    private fun querySize(uri: Uri): Long =
        query(uri) { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
        } ?: -1L

    private fun <T> query(uri: Uri, read: (Cursor) -> T?): T? =
        contentResolver.query(uri, null, null, null, null)?.use(read)

    private fun parseRecordId(responseBody: String): Long? =
        runCatching {
            val root = JSONObject(responseBody)
            val record = root.optJSONObject("record")
            if (record != null && record.has("id")) record.getLong("id") else null
        }.getOrNull()

    private fun parseError(responseBody: String, responseCode: Int): String =
        runCatching { JSONObject(responseBody).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "营养记录接口返回 HTTP $responseCode。"

    private fun Float.formatDecimal(): String =
        String.format(Locale.US, "%.2f", this)

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
