package com.neurafit.app.data

import android.content.Context
import com.neurafit.app.auth.AuthSessionStore

sealed interface TrainingPlanRepositoryResult {
    data class Success(val plan: AiTrainingPlan) : TrainingPlanRepositoryResult
    data class Error(val message: String) : TrainingPlanRepositoryResult
}

class TrainingPlanRepository(
    context: Context,
    private val sessionStore: AuthSessionStore = AuthSessionStore(context.applicationContext),
    private val apiClient: TrainingPlanApiClient = TrainingPlanApiClient(),
) {
    suspend fun recommendPlan(): TrainingPlanRepositoryResult {
        val token = sessionStore.token()
        if (token.isNullOrBlank()) {
            return TrainingPlanRepositoryResult.Error("请先登录后再生成AI训练计划。")
        }

        return when (val result = apiClient.recommendPlan(token)) {
            is TrainingPlanApiResult.Error -> TrainingPlanRepositoryResult.Error(result.message)
            is TrainingPlanApiResult.Success -> TrainingPlanRepositoryResult.Success(result.value)
        }
    }

    suspend fun adoptPlan(plan: AiTrainingPlan): TrainingPlanRepositoryResult {
        val token = sessionStore.token()
        if (token.isNullOrBlank()) {
            return TrainingPlanRepositoryResult.Error("请先登录后再采用训练计划。")
        }

        return when (val result = apiClient.adoptPlan(token, plan)) {
            is TrainingPlanApiResult.Error -> TrainingPlanRepositoryResult.Error(result.message)
            is TrainingPlanApiResult.Success -> TrainingPlanRepositoryResult.Success(result.value)
        }
    }
}
