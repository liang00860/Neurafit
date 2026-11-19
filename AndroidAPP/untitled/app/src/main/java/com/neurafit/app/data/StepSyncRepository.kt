package com.neurafit.app.data

import android.content.Context
import com.neurafit.app.auth.AuthSessionStore

sealed interface StepSyncResult {
    data object Success : StepSyncResult
    data class Error(val message: String) : StepSyncResult
}

class StepSyncRepository(
    context: Context,
    private val sessionStore: AuthSessionStore = AuthSessionStore(context.applicationContext),
    private val apiClient: StepStatsApiClient = StepStatsApiClient(),
) {
    suspend fun sync(records: List<DailyStepRecord>): StepSyncResult {
        if (records.isEmpty()) return StepSyncResult.Success
        val token = sessionStore.token()
        if (token.isNullOrBlank()) {
            return StepSyncResult.Error("请先登录后再同步步数。")
        }
        return when (val result = apiClient.syncStepRecords(records, token)) {
            is StepStatsApiResult.Success -> StepSyncResult.Success
            is StepStatsApiResult.Error -> StepSyncResult.Error(result.message)
        }
    }
}
