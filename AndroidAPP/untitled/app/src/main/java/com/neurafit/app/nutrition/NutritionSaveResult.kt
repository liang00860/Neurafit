package com.neurafit.app.nutrition

sealed interface NutritionSaveResult {
    data class Success(val recordId: Long?) : NutritionSaveResult
    data object NeedsLogin : NutritionSaveResult
    data class Error(val message: String) : NutritionSaveResult
}

sealed interface NutritionSaveUiState {
    data object Idle : NutritionSaveUiState
    data object Saving : NutritionSaveUiState
    data class Saved(val recordId: Long?) : NutritionSaveUiState
    data object NeedsLogin : NutritionSaveUiState
    data class Error(val message: String) : NutritionSaveUiState
}
