package com.neurafit.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neurafit.app.data.AiTrainingPlan
import com.neurafit.app.data.TrainingPlanRepository
import com.neurafit.app.data.TrainingPlanRepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrainingPlanUiState(
    val isLoadingRecommendation: Boolean = false,
    val isAdopting: Boolean = false,
    val isDialogVisible: Boolean = false,
    val previewPlan: AiTrainingPlan? = null,
    val adoptedPlan: AiTrainingPlan? = null,
    val message: String? = null,
)

class TrainingPlanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TrainingPlanRepository(application)
    private val _uiState = MutableStateFlow(TrainingPlanUiState())
    val uiState: StateFlow<TrainingPlanUiState> = _uiState.asStateFlow()

    fun requestPlan() {
        if (_uiState.value.isLoadingRecommendation) return

        _uiState.value = _uiState.value.copy(
            isDialogVisible = true,
            isLoadingRecommendation = true,
            message = null,
            previewPlan = null,
        )
        viewModelScope.launch {
            when (val result = repository.recommendPlan()) {
                is TrainingPlanRepositoryResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingRecommendation = false,
                        message = readableTrainingPlanMessage(
                            result.message,
                            "请先登录后再生成AI训练计划。",
                        ),
                    )
                }
                is TrainingPlanRepositoryResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingRecommendation = false,
                        previewPlan = result.plan,
                        message = null,
                    )
                }
            }
        }
    }

    fun adoptPreviewPlan() {
        val plan = _uiState.value.previewPlan ?: return
        if (_uiState.value.isAdopting) return

        _uiState.value = _uiState.value.copy(isAdopting = true, message = null)
        viewModelScope.launch {
            when (val result = repository.adoptPlan(plan)) {
                is TrainingPlanRepositoryResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isAdopting = false,
                        message = readableTrainingPlanMessage(
                            result.message,
                            "请先登录后再采用训练计划。",
                        ),
                    )
                }
                is TrainingPlanRepositoryResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isAdopting = false,
                        isDialogVisible = false,
                        previewPlan = null,
                        adoptedPlan = result.plan,
                        message = "训练计划已上传服务器，并会同步到Web端日历。",
                    )
                }
            }
        }
    }

    fun dismissDialog() {
        if (_uiState.value.isAdopting) return
        _uiState.value = _uiState.value.copy(
            isDialogVisible = false,
            isLoadingRecommendation = false,
            previewPlan = null,
            message = null,
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

private fun readableTrainingPlanMessage(message: String, fallback: String): String {
    val looksGarbled = message.any { it == '�' } ||
        listOf("璁", "銆", "涓", "鍚", "垝").any { message.contains(it) }
    return if (looksGarbled || message.isBlank()) fallback else message
}
