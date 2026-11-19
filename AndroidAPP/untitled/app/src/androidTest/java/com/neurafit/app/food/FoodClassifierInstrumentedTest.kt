package com.neurafit.app.food

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodClassifierInstrumentedTest {
    @Test
    fun classifiesHamburgerTestAsset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = context.assets.open(TEST_ASSET_NAME).use { input ->
            BitmapFactory.decodeStream(input)
        }

        FoodClassifier(context).use { classifier ->
            val result = classifier.classify(bitmap)
            Log.i(
                TAG,
                "result foodName=${result.foodName}; label=${result.label}; " +
                    "confidence=${result.confidence}; calories=${result.estimatedCaloriesKcal}; " +
                    "items=${result.itemSummary}",
            )
            result.items.forEachIndexed { index, item ->
                Log.i(
                    TAG,
                    "item[$index] foodName=${item.foodName}; label=${item.label}; " +
                        "confidence=${item.confidence}; area=${item.areaFraction}; " +
                        "scale=${item.servingScale}; calories=${item.caloriesKcal}; " +
                        "region=${item.sourceRegion}",
                )
            }
            assertTrue(result.items.isNotEmpty())
        }
    }

    private companion object {
        const val TAG = "FoodClassifierIT"
        const val TEST_ASSET_NAME = "test_food_hamburger.jpg"
    }
}
