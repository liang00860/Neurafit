package com.neurafit.app.nutrition

import android.content.Context
import com.neurafit.app.auth.AuthSessionStore

class RecipeSelectionRepository(
    private val tokenProvider: () -> String?,
    private val uploader: RecipeSelectionUploader,
) {
    constructor(context: Context) : this(
        tokenProvider = { AuthSessionStore(context.applicationContext).token() },
        uploader = RecipeSelectionApiClient(context.applicationContext),
    )

    suspend fun save(record: SelectedRecipeRecord): RecipeSelectionResult {
        val token = tokenProvider()
        if (token.isNullOrBlank()) {
            return RecipeSelectionResult.NeedsLogin
        }
        return uploader.upload(record, token)
    }
}

interface RecipeSelectionUploader {
    suspend fun upload(record: SelectedRecipeRecord, token: String): RecipeSelectionResult
}
