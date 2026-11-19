package com.neurafit.app.nutrition

data class SelectedRecipeRecord(
    val recipeId: String,
    val title: String,
    val imageUrl: String,
    val prepTimeMinutes: Int,
    val caloriesKcal: Int,
    val carbsGrams: Float,
    val proteinGrams: Float,
    val fiberGrams: Float,
    val ingredients: String,
    val steps: String,
)
