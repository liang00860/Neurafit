package com.neurafit.app.food

import kotlin.math.roundToInt
import kotlin.math.sqrt

object FoodLabelMapper {
    fun toFoodName(label: String): String =
        FoodNutritionEstimator.estimate(label).foodName

    fun toClassifyResult(
        label: String,
        confidence: Float,
        caloriesKcal: Int? = null,
        dataSourceLabel: String = "本地营养估算",
    ): FoodClassifyResult {
        val item = toClassifyItem(
            label = label,
            confidence = confidence,
            caloriesKcal = caloriesKcal,
            dataSourceLabel = dataSourceLabel,
        )
        return FoodClassifyResult(
            foodName = item.foodName,
            label = item.label,
            confidence = item.confidence,
            estimatedServingGrams = item.servingGrams,
            estimatedWeightLabel = item.weightLabel,
            estimatedCaloriesKcal = item.caloriesKcal,
            estimatedCaloriesLabel = item.caloriesLabel,
            proteinGrams = item.proteinGrams,
            dietaryFiberGrams = item.dietaryFiberGrams,
            carbsGrams = item.carbsGrams,
            fatGrams = item.fatGrams,
            proteinLabel = item.proteinLabel,
            dietaryFiberLabel = item.dietaryFiberLabel,
            carbsLabel = item.carbsLabel,
            fatLabel = item.fatLabel,
            analysisSummary = FoodNutritionEstimator.estimate(
                label = label,
                caloriesKcal = caloriesKcal,
                dataSourceLabel = dataSourceLabel,
            ).analysisSummary,
            dataSourceLabel = dataSourceLabel,
            itemSummary = item.toSummaryLabel(),
            itemCount = 1,
            items = listOf(item),
        )
    }

    fun toClassifyResult(
        detections: List<FoodAiDetection>,
        caloriesFor: (String) -> Int?,
        dataSourceLabel: String = "本地营养估算",
    ): FoodClassifyResult {
        val items = detections.map { detection ->
            toClassifyItem(
                label = detection.label,
                confidence = detection.confidence,
                caloriesKcal = caloriesFor(detection.label),
                dataSourceLabel = dataSourceLabel,
                servingScale = detection.toServingScale(),
                areaFraction = detection.areaFraction,
                sourceRegion = detection.sourceRegion,
            )
        }
        if (items.isEmpty()) {
            throw FoodClassifierException("未识别到食物，请重新拍摄清晰的食物照片")
        }

        val totalServingGrams = items.sumOf { it.servingGrams }
        val totalCaloriesKcal = items.sumOf { it.caloriesKcal }
        val totalProteinGrams = items.sumOf { it.proteinGrams.toDouble() }.toFloat()
        val totalDietaryFiberGrams = items.sumOf { it.dietaryFiberGrams.toDouble() }.toFloat()
        val totalCarbsGrams = items.sumOf { it.carbsGrams.toDouble() }.toFloat()
        val totalFatGrams = items.sumOf { it.fatGrams.toDouble() }.toFloat()
        val averageConfidence = detections
            .map { it.confidence.coerceIn(0f, 1f) }
            .average()
            .takeIf { !it.isNaN() }
            ?.toFloat()
            ?: 0f

        return FoodClassifyResult(
            foodName = items.toFoodNameSummary(),
            label = items.joinToString(separator = " + ") { it.label },
            confidence = averageConfidence,
            estimatedServingGrams = totalServingGrams,
            estimatedWeightLabel = "约 ${totalServingGrams}g",
            estimatedCaloriesKcal = totalCaloriesKcal,
            estimatedCaloriesLabel = "$totalCaloriesKcal kcal",
            proteinGrams = totalProteinGrams,
            dietaryFiberGrams = totalDietaryFiberGrams,
            carbsGrams = totalCarbsGrams,
            fatGrams = totalFatGrams,
            proteinLabel = totalProteinGrams.toGramLabel(),
            dietaryFiberLabel = totalDietaryFiberGrams.toGramLabel(),
            carbsLabel = totalCarbsGrams.toGramLabel(),
            fatLabel = totalFatGrams.toGramLabel(),
            analysisSummary = items.toAnalysisSummary(),
            dataSourceLabel = dataSourceLabel,
            itemSummary = items.toItemSummary(),
            itemCount = items.size,
            items = items,
        )
    }

    private fun toClassifyItem(
        label: String,
        confidence: Float,
        caloriesKcal: Int?,
        dataSourceLabel: String,
        servingScale: Float = 1f,
        areaFraction: Float = 0f,
        sourceRegion: String = FoodAiImageWindow.FULL_REGION_NAME,
    ): FoodClassifyItem {
        val nutritionEstimate = FoodNutritionEstimator.estimate(
            label = label,
            caloriesKcal = caloriesKcal,
            dataSourceLabel = dataSourceLabel,
        )
        val boundedServingScale = servingScale.coerceIn(MIN_SERVING_SCALE, MAX_SERVING_SCALE)
        val servingGrams = (nutritionEstimate.servingGrams * boundedServingScale)
            .roundToInt()
            .coerceAtLeast(1)
        val calories = (nutritionEstimate.caloriesKcal * boundedServingScale)
            .roundToInt()
            .coerceAtLeast(1)
        val proteinGrams = nutritionEstimate.proteinGrams * boundedServingScale
        val dietaryFiberGrams = nutritionEstimate.dietaryFiberGrams * boundedServingScale
        val carbsGrams = nutritionEstimate.carbsGrams * boundedServingScale
        val fatGrams = nutritionEstimate.fatGrams * boundedServingScale
        return FoodClassifyItem(
            foodName = nutritionEstimate.foodName,
            label = normalize(label),
            confidence = confidence.coerceIn(0f, 1f),
            servingGrams = servingGrams,
            weightLabel = "约 ${servingGrams}g",
            caloriesKcal = calories,
            caloriesLabel = "$calories kcal",
            proteinGrams = proteinGrams,
            dietaryFiberGrams = dietaryFiberGrams,
            carbsGrams = carbsGrams,
            fatGrams = fatGrams,
            proteinLabel = proteinGrams.toGramLabel(),
            dietaryFiberLabel = dietaryFiberGrams.toGramLabel(),
            carbsLabel = carbsGrams.toGramLabel(),
            fatLabel = fatGrams.toGramLabel(),
            areaFraction = areaFraction,
            servingScale = boundedServingScale,
            sourceRegion = sourceRegion,
        )
    }

    private fun List<FoodClassifyItem>.toFoodNameSummary(): String =
        groupedByFoodName().joinToString(separator = " + ") { group ->
            if (group.count == 1) group.foodName else "${group.foodName} x${group.count}"
        }

    private fun List<FoodClassifyItem>.toItemSummary(): String =
        groupedByFoodName().joinToString(separator = " · ") { group ->
            val countLabel = if (group.count == 1) "" else " x${group.count}"
            "${group.foodName}$countLabel ${group.caloriesKcal} kcal"
        }

    private fun List<FoodClassifyItem>.toAnalysisSummary(): String {
        if (size == 1) {
            return FoodNutritionEstimator.estimate(first().label).analysisSummary
        }
        return "识别到 ${size} 项食物：${toFoodNameSummary()}。已按检测结果合并估算总热量和宏量营养。"
    }

    private fun FoodClassifyItem.toSummaryLabel(): String =
        "$foodName $caloriesLabel"

    private fun List<FoodClassifyItem>.groupedByFoodName(): List<FoodItemGroup> =
        groupBy { it.foodName }
            .map { (foodName, items) ->
                FoodItemGroup(
                    foodName = foodName,
                    count = items.size,
                    caloriesKcal = items.sumOf { it.caloriesKcal },
                )
            }

    private fun Float.toGramLabel(): String =
        "${roundToInt()}g"

    private fun FoodAiDetection.toServingScale(): Float {
        if (areaFraction <= 0f) return 1f
        return sqrt((areaFraction / REFERENCE_AREA_FRACTION).toDouble())
            .toFloat()
            .coerceIn(MIN_SERVING_SCALE, MAX_SERVING_SCALE)
    }

    private fun normalize(label: String): String =
        label.trim()
            .lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")

    private const val REFERENCE_AREA_FRACTION = 0.35f
    private const val MIN_SERVING_SCALE = 0.45f
    private const val MAX_SERVING_SCALE = 1.6f
}

private data class FoodItemGroup(
    val foodName: String,
    val count: Int,
    val caloriesKcal: Int,
)
