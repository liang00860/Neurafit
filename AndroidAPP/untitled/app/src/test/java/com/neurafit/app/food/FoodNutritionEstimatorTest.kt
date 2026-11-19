package com.neurafit.app.food

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodNutritionEstimatorTest {
    @Test
    fun mapsKnownFoodLabelToCalorieEstimate() {
        val result = FoodLabelMapper.toClassifyResult(
            label = "Hamburger",
            confidence = 0.92f,
            caloriesKcal = 354,
            dataSourceLabel = "Food.AI 热量表 + 本地宏量估算",
        )

        assertEquals("汉堡", result.foodName)
        assertEquals(220, result.estimatedServingGrams)
        assertEquals(354, result.estimatedCaloriesKcal)
        assertEquals("354 kcal", result.estimatedCaloriesLabel)
        assertEquals("19g", result.proteinLabel)
        assertEquals(2.3f, result.dietaryFiberGrams, 0.01f)
        assertEquals("2g", result.dietaryFiberLabel)
        assertEquals("Food.AI 热量表 + 本地宏量估算", result.dataSourceLabel)
        assertEquals(1, result.itemCount)
        assertEquals("汉堡 354 kcal", result.itemSummary)
    }

    @Test
    fun usesFallbackNutritionForUnknownFoodLabel() {
        val result = FoodLabelMapper.toClassifyResult(
            label = "mystery_food",
            confidence = 1.5f,
        )

        assertEquals("mystery_food", result.foodName)
        assertEquals(250, result.estimatedServingGrams)
        assertEquals(450, result.estimatedCaloriesKcal)
        assertEquals("4g", result.dietaryFiberLabel)
        assertEquals(1f, result.confidence)
    }

    @Test
    fun aggregatesMultipleDetectedFoodsIntoOneNutritionResult() {
        val result = FoodLabelMapper.toClassifyResult(
            detections = listOf(
                FoodAiDetection(label = "Apple", confidence = 0.99f),
                FoodAiDetection(label = "Orange", confidence = 0.82f),
            ),
            caloriesFor = { label ->
                when (label.lowercase()) {
                    "apple" -> 95
                    "orange" -> 45
                    else -> null
                }
            },
            dataSourceLabel = "Food.AI 热量表 + 本地宏量估算",
        )

        assertEquals("苹果 + 橙子", result.foodName)
        assertEquals(2, result.itemCount)
        assertEquals(140, result.estimatedCaloriesKcal)
        assertEquals("140 kcal", result.estimatedCaloriesLabel)
        assertEquals("苹果 95 kcal · 橙子 45 kcal", result.itemSummary)
        assertEquals(310, result.estimatedServingGrams)
    }

    @Test
    fun scalesDetectedServingByVisibleFoodArea() {
        val result = FoodLabelMapper.toClassifyResult(
            detections = listOf(
                FoodAiDetection(
                    label = "Pizza",
                    confidence = 0.88f,
                    areaFraction = 0.0875f,
                ),
            ),
            caloriesFor = { label ->
                if (label.equals("pizza", ignoreCase = true)) 285 else null
            },
            dataSourceLabel = "Food.AI 热量表 + 本地宏量估算",
        )

        assertEquals("披萨", result.foodName)
        assertEquals(70, result.estimatedServingGrams)
        assertEquals(143, result.estimatedCaloriesKcal)
        assertEquals("披萨 143 kcal", result.itemSummary)
        assertEquals(0.5f, result.items.first().servingScale, 0.01f)
    }
}
