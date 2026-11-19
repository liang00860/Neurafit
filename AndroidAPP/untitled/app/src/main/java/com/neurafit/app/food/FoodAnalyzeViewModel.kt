package com.neurafit.app.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FoodAnalyzeViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        FoodAnalyzeUiState(
            selectedImageUri = savedStateHandle[SELECTED_IMAGE_URI_KEY],
            hasPreviewImage = savedStateHandle.get<String>(SELECTED_IMAGE_URI_KEY) != null,
            previewSourceLabel = if (savedStateHandle.get<String>(SELECTED_IMAGE_URI_KEY) != null) {
                "已选择图片"
            } else {
                "未选择图片"
            },
            previewStatusLabel = if (savedStateHandle.get<String>(SELECTED_IMAGE_URI_KEY) != null) {
                "食物图片已加载"
            } else {
                "等待食物图片"
            },
        ),
    )
    val uiState: StateFlow<FoodAnalyzeUiState> = _uiState.asStateFlow()

    fun onImageSelected(imageUri: String?, sourceLabel: String) {
        if (imageUri.isNullOrBlank()) {
            showMessage("未选择图片")
            return
        }
        savedStateHandle[SELECTED_IMAGE_URI_KEY] = imageUri
        _uiState.update {
            it.copy(
                selectedImageUri = imageUri,
                recognitionState = FoodRecognitionState.Idle,
                hasPreviewImage = true,
                previewSourceLabel = sourceLabel,
                previewStatusLabel = "食物图片已加载",
                foodName = "--",
                confidenceLabel = "--",
                estimatedWeightLabel = "--",
                estimatedCaloriesLabel = "--",
                hasAnalysisResult = false,
                saveMessage = null,
            )
        }
    }

    fun analyzeFood() {
        if (_uiState.value.selectedImageUri.isNullOrBlank()) {
            showMessage("请先选择食物图片")
            return
        }
        val fakeResult = FoodLabelMapper.toClassifyResult(DEMO_LABEL, DEMO_CONFIDENCE)
        onRecognitionSuccess(fakeResult)
    }

    fun markRecognitionLoading() {
        _uiState.update { current ->
            current.copy(
                recognitionState = FoodRecognitionState.Loading,
                previewStatusLabel = "正在识别食物",
                hasAnalysisResult = false,
                saveMessage = null,
            )
        }
    }

    fun onRecognitionSuccess(result: FoodClassifyResult) {
        _uiState.update { current ->
            current.copy(
                recognitionState = FoodRecognitionState.Success(result),
                previewStatusLabel = "已生成分析结果",
                foodName = result.foodName,
                confidenceLabel = result.confidence.toPercentLabel(),
                estimatedWeightLabel = result.estimatedWeightLabel,
                estimatedCaloriesLabel = result.estimatedCaloriesLabel,
                hasAnalysisResult = true,
                saveMessage = null,
            )
        }
    }

    fun onRecognitionError(message: String) {
        _uiState.update { current ->
            current.copy(
                recognitionState = FoodRecognitionState.Error(message),
                previewStatusLabel = "识别失败",
                foodName = "--",
                confidenceLabel = "--",
                hasAnalysisResult = false,
                saveMessage = message,
            )
        }
    }

    fun saveRecord() {
        _uiState.update { current ->
            current.copy(
                saveMessage = if (current.hasAnalysisResult) {
                    "饮食记录保存演示完成，后续阶段将接入 Room。"
                } else {
                    "请先完成食物分析再保存。"
                },
            )
        }
    }

    fun showMessage(message: String) {
        _uiState.update { current ->
            current.copy(saveMessage = message)
        }
    }

    fun consumeSaveMessage() {
        _uiState.update { current ->
            current.copy(saveMessage = null)
        }
    }

    private companion object {
        const val SELECTED_IMAGE_URI_KEY = "selectedImageUri"
        const val DEMO_LABEL = "chicken_breast"
        const val DEMO_CONFIDENCE = 0.92f
    }
}

private fun Float.toPercentLabel(): String =
    "${(this * 100).toInt()}%"
