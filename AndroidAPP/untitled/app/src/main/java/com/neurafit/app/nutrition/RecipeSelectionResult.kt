package com.neurafit.app.nutrition

sealed interface RecipeSelectionResult {
    data class Success(val recordId: Long?) : RecipeSelectionResult
    data object NeedsLogin : RecipeSelectionResult
    data class Error(val message: String) : RecipeSelectionResult
}

sealed interface RecipeSelectionUiState {
    data object Idle : RecipeSelectionUiState
    data object Saving : RecipeSelectionUiState
    data class Saved(val recordId: Long?) : RecipeSelectionUiState
    data object NeedsLogin : RecipeSelectionUiState
    data class Error(val message: String) : RecipeSelectionUiState
}
