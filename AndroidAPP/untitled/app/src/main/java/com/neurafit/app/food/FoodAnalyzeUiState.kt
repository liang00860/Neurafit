package com.neurafit.app.food

data class FoodAnalyzeUiState(
    val selectedImageUri: String? = null,
    val recognitionState: FoodRecognitionState = FoodRecognitionState.Idle,
    val hasPreviewImage: Boolean = false,
    val previewSourceLabel: String = "未选择图片",
    val previewStatusLabel: String = "等待食物图片",
    val foodName: String = "--",
    val confidenceLabel: String = "--",
    val estimatedWeightLabel: String = "--",
    val estimatedCaloriesLabel: String = "--",
    val hasAnalysisResult: Boolean = false,
    val saveMessage: String? = null,
)
