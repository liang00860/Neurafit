package com.neurafit.app.food

import com.neurafit.app.FoodAnalyzeActivity
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodAnalyzeExtrasTest {
    @Test
    fun scanResultExtrasExposeDietaryFiberAndNumericFields() {
        val extras = listOf(
            FoodAnalyzeActivity.EXTRA_DIETARY_FIBER,
            FoodAnalyzeActivity.EXTRA_DIETARY_FIBER_GRAMS,
            FoodAnalyzeActivity.EXTRA_CALORIES_KCAL,
            FoodAnalyzeActivity.EXTRA_PROTEIN_GRAMS,
            FoodAnalyzeActivity.EXTRA_CARBS_GRAMS,
            FoodAnalyzeActivity.EXTRA_FAT_GRAMS,
            FoodAnalyzeActivity.EXTRA_ITEM_SUMMARY,
        )

        assertTrue(extras.all { it.startsWith("com.neurafit.app.extra.") })
    }
}
