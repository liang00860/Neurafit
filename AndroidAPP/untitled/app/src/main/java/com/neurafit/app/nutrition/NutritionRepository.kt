package com.neurafit.app.nutrition

import android.content.Context
import com.neurafit.app.auth.AuthSessionStore

class NutritionRepository(
    private val tokenProvider: () -> String?,
    private val uploader: NutritionUploader,
) {
    constructor(context: Context) : this(
        tokenProvider = { AuthSessionStore(context.applicationContext).token() },
        uploader = NutritionApiClient(context.applicationContext),
    )

    suspend fun save(record: NutritionScanRecord): NutritionSaveResult {
        val token = tokenProvider()
        if (token.isNullOrBlank()) {
            return NutritionSaveResult.NeedsLogin
        }
        return uploader.upload(record, token)
    }
}

interface NutritionUploader {
    suspend fun upload(record: NutritionScanRecord, token: String): NutritionSaveResult
}
