package com.neurafit.app.food

import kotlin.math.roundToInt

object FoodNutritionEstimator {
    private val profiles = mapOf(
        "bread" to FoodNutritionProfile("面包", 35, 79, 0.14f, 0.74f, 0.12f, 1.1f, "以碳水为主，适合搭配蛋白质食物让饱腹更稳定。"),
        "pancake" to FoodNutritionProfile("薄煎饼", 110, 175, 0.10f, 0.60f, 0.30f, 1.5f, "碳水和脂肪都偏中等，建议控制糖浆或奶油用量。"),
        "waffle" to FoodNutritionProfile("华夫饼", 90, 218, 0.08f, 0.54f, 0.38f, 1.1f, "脂肪占比较高，搭配水果会比额外甜酱更轻盈。"),
        "bagel" to FoodNutritionProfile("贝果", 100, 245, 0.14f, 0.78f, 0.08f, 2.3f, "主要提供碳水，训练前后补能比较合适。"),
        "muffin" to FoodNutritionProfile("马芬", 120, 426, 0.06f, 0.52f, 0.42f, 1.8f, "热量密度较高，更适合作为小份甜点而不是正餐主食。"),
        "doughnut" to FoodNutritionProfile("甜甜圈", 70, 195, 0.05f, 0.50f, 0.45f, 0.9f, "糖和脂肪占比较高，建议搭配无糖饮品并控制频率。"),
        "hamburger" to FoodNutritionProfile("汉堡", 220, 354, 0.22f, 0.38f, 0.40f, 2.3f, "蛋白质、碳水和脂肪都较集中，下一餐可以增加蔬菜比例。"),
        "pizza" to FoodNutritionProfile("披萨", 140, 285, 0.18f, 0.42f, 0.40f, 2.0f, "脂肪和碳水都不低，建议按片控制份量。"),
        "sandwich" to FoodNutritionProfile("三明治", 180, 340, 0.22f, 0.46f, 0.32f, 3.0f, "营养结构相对均衡，优先选择瘦肉和蔬菜较多的版本。"),
        "hot dog" to FoodNutritionProfile("热狗", 100, 151, 0.20f, 0.30f, 0.50f, 1.0f, "脂肪比例偏高，建议搭配清淡蔬菜或水果。"),
        "french fries" to FoodNutritionProfile("薯条", 140, 365, 0.05f, 0.50f, 0.45f, 4.6f, "油脂和碳水密集，适合小份分享而不是作为主食。"),
        "apple" to FoodNutritionProfile("苹果", 180, 95, 0.02f, 0.94f, 0.04f, 4.4f, "低脂且富含水果碳水，适合作为轻量加餐。"),
        "orange" to FoodNutritionProfile("橙子", 130, 45, 0.06f, 0.90f, 0.04f, 3.1f, "热量较低，适合补充水分和水果碳水。"),
        "banana" to FoodNutritionProfile("香蕉", 120, 105, 0.04f, 0.92f, 0.04f, 3.1f, "快速补充碳水，训练前后都很友好。"),
        "grape" to FoodNutritionProfile("葡萄", 90, 62, 0.03f, 0.93f, 0.04f, 0.8f, "热量较轻，但糖分吸收快，建议按小份食用。"),
        "rice" to FoodNutritionProfile("米饭", 150, 174, 0.08f, 0.90f, 0.02f, 0.6f, "以碳水供能为主，搭配蛋白质和蔬菜会更均衡。"),
        "chicken breast" to FoodNutritionProfile("鸡胸肉", 150, 248, 0.78f, 0.02f, 0.20f, 0.0f, "高蛋白、低碳水，适合增肌或控制热量时作为主菜。"),
        "egg" to FoodNutritionProfile("鸡蛋", 50, 78, 0.35f, 0.02f, 0.63f, 0.0f, "蛋白质和脂肪比例均衡，适合作为早餐或加餐。"),
        "dumpling" to FoodNutritionProfile("饺子", 200, 460, 0.18f, 0.54f, 0.28f, 3.5f, "碳水和脂肪都较集中，建议搭配清淡蔬菜汤。"),
        "noodle" to FoodNutritionProfile("面条", 250, 345, 0.12f, 0.76f, 0.12f, 2.5f, "主食属性明显，注意汤底和浇头带来的额外热量。"),
        "salad" to FoodNutritionProfile("沙拉", 300, 420, 0.22f, 0.34f, 0.44f, 6.0f, "热量主要取决于酱料和坚果，清爽版本更适合恢复餐。"),
    )
    private val fallbackProfile = FoodNutritionProfile(
        foodName = null,
        servingGrams = 250,
        defaultCaloriesKcal = 450,
        proteinRatio = 0.18f,
        carbsRatio = 0.52f,
        fatRatio = 0.30f,
        dietaryFiberGrams = 4.0f,
        summary = "未匹配到详细营养模板，已按常见混合餐估算。",
    )

    fun estimate(
        label: String,
        caloriesKcal: Int? = null,
        dataSourceLabel: String = DEFAULT_SOURCE_LABEL,
    ): FoodNutritionEstimate {
        val normalizedLabel = normalize(label)
        val profile = profiles[normalizedLabel] ?: fallbackProfile
        val calories = caloriesKcal ?: profile.defaultCaloriesKcal
        val proteinGrams = calories * profile.proteinRatio / PROTEIN_CALORIES_PER_GRAM
        val carbsGrams = calories * profile.carbsRatio / CARB_CALORIES_PER_GRAM
        val fatGrams = calories * profile.fatRatio / FAT_CALORIES_PER_GRAM
        return FoodNutritionEstimate(
            foodName = profile.foodName ?: label,
            servingGrams = profile.servingGrams,
            caloriesKcal = calories,
            weightLabel = "约 ${profile.servingGrams}g",
            caloriesLabel = "$calories kcal",
            proteinGrams = proteinGrams,
            dietaryFiberGrams = profile.dietaryFiberGrams,
            carbsGrams = carbsGrams,
            fatGrams = fatGrams,
            proteinLabel = "${proteinGrams.roundToInt()}g",
            dietaryFiberLabel = "${profile.dietaryFiberGrams.roundToInt()}g",
            carbsLabel = "${carbsGrams.roundToInt()}g",
            fatLabel = "${fatGrams.roundToInt()}g",
            analysisSummary = profile.summary,
            dataSourceLabel = dataSourceLabel,
        )
    }

    private fun normalize(label: String): String =
        label.trim()
            .lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")

    private const val DEFAULT_SOURCE_LABEL = "本地营养估算"
    private const val PROTEIN_CALORIES_PER_GRAM = 4f
    private const val CARB_CALORIES_PER_GRAM = 4f
    private const val FAT_CALORIES_PER_GRAM = 9f
}

data class FoodNutritionEstimate(
    val foodName: String,
    val servingGrams: Int,
    val caloriesKcal: Int,
    val weightLabel: String,
    val caloriesLabel: String,
    val proteinGrams: Float,
    val dietaryFiberGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val proteinLabel: String,
    val dietaryFiberLabel: String,
    val carbsLabel: String,
    val fatLabel: String,
    val analysisSummary: String,
    val dataSourceLabel: String,
)

private data class FoodNutritionProfile(
    val foodName: String?,
    val servingGrams: Int,
    val defaultCaloriesKcal: Int,
    val proteinRatio: Float,
    val carbsRatio: Float,
    val fatRatio: Float,
    val dietaryFiberGrams: Float,
    val summary: String,
)
