package com.neurafit.app.food

data class FoodAiDetection(
    val label: String,
    val confidence: Float,
    val areaFraction: Float = 0f,
    val box: FoodAiBox? = null,
    val sourceRegion: String = FoodAiImageWindow.FULL_REGION_NAME,
)

data class FoodAiBox(
    val top: Float,
    val left: Float,
    val bottom: Float,
    val right: Float,
) {
    val areaFraction: Float
        get() {
            val width = (right - left).coerceAtLeast(0f)
            val height = (bottom - top).coerceAtLeast(0f)
            return (width * height).coerceIn(0f, 1f)
        }

    fun iou(other: FoodAiBox): Float {
        val intersectionArea = intersectionArea(other)
        val unionArea = areaFraction + other.areaFraction - intersectionArea
        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }

    fun smallerOverlap(other: FoodAiBox): Float {
        val smallerArea = minOf(areaFraction, other.areaFraction)
        if (smallerArea <= 0f) return 0f
        return intersectionArea(other) / smallerArea
    }

    private fun intersectionArea(other: FoodAiBox): Float {
        val intersectionTop = maxOf(top, other.top)
        val intersectionLeft = maxOf(left, other.left)
        val intersectionBottom = minOf(bottom, other.bottom)
        val intersectionRight = minOf(right, other.right)
        val intersectionWidth = (intersectionRight - intersectionLeft).coerceAtLeast(0f)
        val intersectionHeight = (intersectionBottom - intersectionTop).coerceAtLeast(0f)
        return intersectionWidth * intersectionHeight
    }
}

data class FoodAiImageWindow(
    val name: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float
        get() = (right - left).coerceAtLeast(0f)

    val height: Float
        get() = (bottom - top).coerceAtLeast(0f)

    fun mapBox(localBox: FoodAiBox): FoodAiBox =
        FoodAiBox(
            top = (top + localBox.top * height).coerceIn(0f, 1f),
            left = (left + localBox.left * width).coerceIn(0f, 1f),
            bottom = (top + localBox.bottom * height).coerceIn(0f, 1f),
            right = (left + localBox.right * width).coerceIn(0f, 1f),
        )

    companion object {
        const val FULL_REGION_NAME = "full"

        val Full = FoodAiImageWindow(
            name = FULL_REGION_NAME,
            left = 0f,
            top = 0f,
            right = 1f,
            bottom = 1f,
        )
    }
}

class FoodAiLabelMap private constructor(
    private val labels: List<String>,
) {
    fun labelForOutputClass(rawClass: Float): String? {
        val labelOffset = if (labels.firstOrNull() == UNKNOWN_LABEL) 1 else 0
        return labels.getOrNull(rawClass.toInt() + labelOffset)
    }

    companion object {
        private const val UNKNOWN_LABEL = "???"

        fun parse(lines: Sequence<String>): FoodAiLabelMap {
            val labels = lines.map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (labels.isEmpty()) {
                throw FoodClassifierException("food_labelmap.txt 为空，请检查 Food.AI 标签文件")
            }
            return FoodAiLabelMap(labels)
        }
    }
}

object FoodAiCalorieTable {
    fun parse(lines: Sequence<String>): Map<String, Int> {
        return lines.mapNotNull { line ->
            val parts = line.split(":", limit = 2)
            val label = parts.getOrNull(0)?.trim()
            val calories = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (label.isNullOrEmpty() || calories == null) {
                null
            } else {
                normalize(label) to calories
            }
        }.toMap()
    }

    fun caloriesFor(label: String, table: Map<String, Int>): Int? =
        table[normalize(label)]

    private fun normalize(label: String): String =
        label.trim().lowercase()
}

object FoodAiDetectionSelector {
    fun selectBest(
        labelMap: FoodAiLabelMap,
        classes: FloatArray,
        scores: FloatArray,
        minConfidence: Float,
        detectionCount: Int,
        locations: Array<FloatArray>? = null,
    ): FoodAiDetection? {
        return selectAll(
            labelMap = labelMap,
            classes = classes,
            scores = scores,
            minConfidence = minConfidence,
            detectionCount = detectionCount,
            locations = locations,
        ).firstOrNull()
    }

    fun selectAll(
        labelMap: FoodAiLabelMap,
        classes: FloatArray,
        scores: FloatArray,
        minConfidence: Float,
        detectionCount: Int,
        locations: Array<FloatArray>? = null,
        imageWindow: FoodAiImageWindow = FoodAiImageWindow.Full,
    ): List<FoodAiDetection> {
        val count = minOf(classes.size, scores.size, detectionCount, locations?.size ?: Int.MAX_VALUE)
        return (0 until count)
            .mapNotNull { index ->
                val confidence = scores[index]
                val label = labelMap.labelForOutputClass(classes[index])
                if (label == null || confidence < minConfidence) {
                    null
                } else {
                    val box = locations
                        ?.getOrNull(index)
                        ?.toFoodAiBox()
                        ?.let(imageWindow::mapBox)
                    FoodAiDetection(
                        label = label,
                        confidence = confidence,
                        areaFraction = box?.areaFraction ?: 0f,
                        box = box,
                        sourceRegion = imageWindow.name,
                    )
                }
            }
            .sortedWith(
                compareByDescending<FoodAiDetection> { it.confidence }
                    .thenByDescending { it.areaFraction },
            )
    }

    fun suppressOverlaps(
        detections: List<FoodAiDetection>,
        iouThreshold: Float,
        maxResults: Int,
    ): List<FoodAiDetection> {
        if (detections.isEmpty() || maxResults <= 0) return emptyList()

        val selected = mutableListOf<FoodAiDetection>()
        detections
            .sortedWith(
                compareByDescending<FoodAiDetection> { it.confidence }
                    .thenByDescending { it.areaFraction },
            )
            .forEach { candidate ->
                val overlapsSelected = selected.any { selectedDetection ->
                    candidate.label.equals(selectedDetection.label, ignoreCase = true) &&
                        candidate.box != null &&
                        selectedDetection.box != null &&
                        (
                            candidate.box.iou(selectedDetection.box) >= iouThreshold ||
                                candidate.box.smallerOverlap(selectedDetection.box) >= SMALLER_OVERLAP_THRESHOLD
                            )
                }
                if (!overlapsSelected) {
                    selected += candidate
                }
                if (selected.size >= maxResults) {
                    return selected
                }
            }
        return selected
    }

    private fun FloatArray.toFoodAiBox(): FoodAiBox? {
        if (size < 4) return null
        val top = minOf(this[0], this[2]).coerceIn(0f, 1f)
        val left = minOf(this[1], this[3]).coerceIn(0f, 1f)
        val bottom = maxOf(this[0], this[2]).coerceIn(0f, 1f)
        val right = maxOf(this[1], this[3]).coerceIn(0f, 1f)
        if (bottom <= top || right <= left) return null
        return FoodAiBox(
            top = top,
            left = left,
            bottom = bottom,
            right = right,
        )
    }

    private const val SMALLER_OVERLAP_THRESHOLD = 0.68f
}
