package com.neurafit.app.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FoodAiDetectionModelsTest {
    @Test
    fun labelMapUsesUnknownFirstLineAsClassOffset() {
        val labelMap = FoodAiLabelMap.parse(
            sequenceOf("???", "Bread", "Pancake", "Waffle", "Bagel", "Muffin", "Doughnut", "Hamburger"),
        )

        assertEquals("Hamburger", labelMap.labelForOutputClass(6f))
    }

    @Test
    fun calorieTableParsesFoodAiFormat() {
        val calorieTable = FoodAiCalorieTable.parse(
            sequenceOf("Bread: 79", "Hamburger: 354", "French fries: 365"),
        )

        assertEquals(79, FoodAiCalorieTable.caloriesFor("bread", calorieTable))
        assertEquals(354, FoodAiCalorieTable.caloriesFor("Hamburger", calorieTable))
        assertEquals(365, FoodAiCalorieTable.caloriesFor("French fries", calorieTable))
    }

    @Test
    fun detectionSelectorReturnsHighestConfidentFood() {
        val labelMap = FoodAiLabelMap.parse(sequenceOf("???", "Bread", "Hamburger", "Pizza"))

        val detection = FoodAiDetectionSelector.selectBest(
            labelMap = labelMap,
            classes = floatArrayOf(0f, 1f, 2f),
            scores = floatArrayOf(0.41f, 0.88f, 0.72f),
            minConfidence = 0.4f,
            detectionCount = 3,
        )

        assertNotNull(detection)
        assertEquals("Hamburger", detection?.label)
        assertEquals(0.88f, detection?.confidence ?: 0f)
    }

    @Test
    fun detectionSelectorReturnsMultipleConfidentFoods() {
        val labelMap = FoodAiLabelMap.parse(sequenceOf("???", "Bread", "Hamburger", "Pizza", "Apple"))

        val detections = FoodAiDetectionSelector.selectAll(
            labelMap = labelMap,
            classes = floatArrayOf(0f, 3f, 2f),
            scores = floatArrayOf(0.41f, 0.91f, 0.72f),
            locations = arrayOf(
                floatArrayOf(0.1f, 0.1f, 0.3f, 0.3f),
                floatArrayOf(0.2f, 0.2f, 0.8f, 0.8f),
                floatArrayOf(0.0f, 0.0f, 0.1f, 0.1f),
            ),
            minConfidence = 0.4f,
            detectionCount = 3,
        )

        assertEquals(3, detections.size)
        assertEquals("Apple", detections[0].label)
        assertEquals("Pizza", detections[1].label)
        assertEquals("Bread", detections[2].label)
        assertEquals(0.36f, detections[0].areaFraction, 0.01f)
    }

    @Test
    fun detectionSelectorMapsCropBoxesBackToFullImage() {
        val labelMap = FoodAiLabelMap.parse(sequenceOf("???", "Apple"))

        val detections = FoodAiDetectionSelector.selectAll(
            labelMap = labelMap,
            classes = floatArrayOf(0f),
            scores = floatArrayOf(0.95f),
            locations = arrayOf(floatArrayOf(0.2f, 0.2f, 0.6f, 0.6f)),
            minConfidence = 0.4f,
            detectionCount = 1,
            imageWindow = FoodAiImageWindow(
                name = "center",
                left = 0.25f,
                top = 0.25f,
                right = 0.75f,
                bottom = 0.75f,
            ),
        )

        val box = detections.first().box
        assertNotNull(box)
        assertEquals(0.35f, box?.top ?: 0f, 0.01f)
        assertEquals(0.35f, box?.left ?: 0f, 0.01f)
        assertEquals(0.55f, box?.bottom ?: 0f, 0.01f)
        assertEquals(0.55f, box?.right ?: 0f, 0.01f)
        assertEquals(0.04f, detections.first().areaFraction, 0.01f)
    }

    @Test
    fun detectionSelectorSuppressesOverlappingDuplicateLabels() {
        val detections = FoodAiDetectionSelector.suppressOverlaps(
            detections = listOf(
                FoodAiDetection(
                    label = "Apple",
                    confidence = 0.93f,
                    areaFraction = 0.25f,
                    box = FoodAiBox(top = 0f, left = 0f, bottom = 0.5f, right = 0.5f),
                ),
                FoodAiDetection(
                    label = "Apple",
                    confidence = 0.82f,
                    areaFraction = 0.25f,
                    box = FoodAiBox(top = 0.05f, left = 0.05f, bottom = 0.55f, right = 0.55f),
                ),
                FoodAiDetection(
                    label = "Orange",
                    confidence = 0.79f,
                    areaFraction = 0.16f,
                    box = FoodAiBox(top = 0.6f, left = 0.6f, bottom = 1f, right = 1f),
                ),
            ),
            iouThreshold = 0.45f,
            maxResults = 5,
        )

        assertEquals(2, detections.size)
        assertEquals("Apple", detections[0].label)
        assertEquals(0.93f, detections[0].confidence, 0.01f)
        assertEquals("Orange", detections[1].label)
    }

    @Test
    fun detectionSelectorSuppressesContainedDuplicateLabelsFromCrops() {
        val detections = FoodAiDetectionSelector.suppressOverlaps(
            detections = listOf(
                FoodAiDetection(
                    label = "Hamburger",
                    confidence = 0.99f,
                    areaFraction = 0.46f,
                    box = FoodAiBox(top = 0.2f, left = 0.1f, bottom = 0.8f, right = 0.87f),
                    sourceRegion = "full",
                ),
                FoodAiDetection(
                    label = "Hamburger",
                    confidence = 0.98f,
                    areaFraction = 0.2f,
                    box = FoodAiBox(top = 0.42f, left = 0.42f, bottom = 0.8f, right = 0.94f),
                    sourceRegion = "bottom_right",
                ),
            ),
            iouThreshold = 0.45f,
            maxResults = 5,
        )

        assertEquals(1, detections.size)
        assertEquals("full", detections.first().sourceRegion)
    }

    @Test
    fun detectionSelectorFiltersLowConfidenceResults() {
        val labelMap = FoodAiLabelMap.parse(sequenceOf("???", "Bread", "Hamburger"))

        val detection = FoodAiDetectionSelector.selectBest(
            labelMap = labelMap,
            classes = floatArrayOf(0f, 1f),
            scores = floatArrayOf(0.20f, 0.39f),
            minConfidence = 0.4f,
            detectionCount = 2,
        )

        assertNull(detection)
    }
}
