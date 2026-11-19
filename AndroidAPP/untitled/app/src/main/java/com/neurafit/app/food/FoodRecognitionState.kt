package com.neurafit.app.food

sealed interface FoodRecognitionState {
    data object Idle : FoodRecognitionState
    data object Loading : FoodRecognitionState
    data class Success(val result: FoodClassifyResult) : FoodRecognitionState
    data class Error(val message: String) : FoodRecognitionState
}
