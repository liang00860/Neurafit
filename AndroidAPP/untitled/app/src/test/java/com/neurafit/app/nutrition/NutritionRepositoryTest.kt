package com.neurafit.app.nutrition

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionRepositoryTest {
    @Test
    fun noTokenDoesNotUpload() = runBlocking {
        var uploadCalled = false
        val repository = NutritionRepository(
            tokenProvider = { null },
            uploader = object : NutritionUploader {
                override suspend fun upload(record: NutritionScanRecord, token: String): NutritionSaveResult {
                    uploadCalled = true
                    return NutritionSaveResult.Success(1L)
                }
            },
        )

        val result = repository.save(testRecord())

        assertEquals(NutritionSaveResult.NeedsLogin, result)
        assertFalse(uploadCalled)
    }

    @Test
    fun tokenUploadsRecord() = runBlocking {
        var uploadedToken: String? = null
        val repository = NutritionRepository(
            tokenProvider = { "session-token" },
            uploader = object : NutritionUploader {
                override suspend fun upload(record: NutritionScanRecord, token: String): NutritionSaveResult {
                    uploadedToken = token
                    return NutritionSaveResult.Success(7L)
                }
            },
        )

        val result = repository.save(testRecord())

        assertEquals(NutritionSaveResult.Success(7L), result)
        assertEquals("session-token", uploadedToken)
    }

    @Test
    fun saveGateSkipsSameScanUntilForced() {
        val gate = NutritionSaveGate()

        assertTrue(gate.shouldSubmit("scan-1"))
        assertFalse(gate.shouldSubmit("scan-1"))
        assertTrue(gate.shouldSubmit("scan-1", force = true))
        assertFalse(gate.shouldSubmit(""))
    }

    private fun testRecord(): NutritionScanRecord =
        NutritionScanRecord(
            scanId = "scan-1",
            imageUri = "content://food/photo.jpg",
            foodName = "汉堡",
            foodLabel = "hamburger",
            confidence = 0.92f,
            confidenceLabel = "92%",
            servingGrams = 220,
            weightLabel = "约 220g",
            caloriesKcal = 354,
            caloriesLabel = "354 kcal",
            proteinGrams = 19.47f,
            proteinLabel = "19g",
            dietaryFiberGrams = 2.3f,
            dietaryFiberLabel = "2g",
            carbsGrams = 33.63f,
            carbsLabel = "34g",
            fatGrams = 15.73f,
            fatLabel = "16g",
            analysisSummary = "估算分析",
            dataSource = "Food.AI 热量表 + 本地营养估算",
            itemSummary = "汉堡 354 kcal",
        )
}
