package com.neurafit.app.data

import android.content.Context
import com.neurafit.app.auth.AuthSessionStore

sealed interface TrainingDurationRepositoryResult {
    data class Success(val summary: TrainingDurationSummary) : TrainingDurationRepositoryResult
    data class Error(val message: String) : TrainingDurationRepositoryResult
}

class TrainingDurationRepository(
    context: Context,
    private val trainingRepository: TrainingRepository = TrainingRepository.create(context.applicationContext),
    private val sessionStore: AuthSessionStore = AuthSessionStore(context.applicationContext),
    private val apiClient: TrainingDurationApiClient = TrainingDurationApiClient(),
) {
    suspend fun syncAndFetchSummary(): TrainingDurationRepositoryResult {
        val token = sessionStore.token()
        if (token.isNullOrBlank()) {
            return TrainingDurationRepositoryResult.Error("请先登录后再加载训练时长。")
        }

        val sessions = trainingRepository.getTrainingRecords()
            .mapNotNull { record ->
                val durationMs = record.durationMs?.takeIf { it >= 0L } ?: return@mapNotNull null
                TrainingDurationSessionPayload(
                    clientTrainingId = record.id,
                    actionName = record.actionName,
                    startedAtMs = record.timestamp,
                    durationMs = durationMs,
                    repCount = record.count,
                    averageScore = record.score,
                )
            }

        when (val syncResult = apiClient.syncSessions(sessions, token)) {
            is TrainingDurationApiResult.Error -> {
                return TrainingDurationRepositoryResult.Error(syncResult.message)
            }
            is TrainingDurationApiResult.Success -> Unit
        }

        return when (val summaryResult = apiClient.fetchSummary(token)) {
            is TrainingDurationApiResult.Error -> TrainingDurationRepositoryResult.Error(summaryResult.message)
            is TrainingDurationApiResult.Success -> TrainingDurationRepositoryResult.Success(summaryResult.value)
        }
    }
}
