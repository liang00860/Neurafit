package com.neurafit.app.evaluation

import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.PushUpThreshold
import com.neurafit.app.analysis.SquatThreshold

data class SamplePrediction(
    val sampleId: String,
    val expectedErrors: Set<ActionErrorType>,
    val predictedErrors: Set<ActionErrorType>,
    val score: Int,
) {
    val isExactMatch: Boolean
        get() = normalized(expectedErrors) == normalized(predictedErrors)

    private fun normalized(errors: Set<ActionErrorType>): Set<ActionErrorType> =
        if (errors.isEmpty()) setOf(ActionErrorType.NONE) else errors
}

data class EvaluationMetrics(
    val sampleCount: Int,
    val exactMatchCount: Int,
    val exactMatchAccuracy: Double,
    val perError: Map<ActionErrorType, ErrorMetric>,
)

data class ErrorMetric(
    val truePositive: Int,
    val falsePositive: Int,
    val falseNegative: Int,
) {
    val precision: Double
        get() = truePositive.toDouble() / (truePositive + falsePositive).coerceAtLeast(1)

    val recall: Double
        get() = truePositive.toDouble() / (truePositive + falseNegative).coerceAtLeast(1)

    val f1: Double
        get() {
            val denominator = precision + recall
            return if (denominator == 0.0) 0.0 else 2 * precision * recall / denominator
        }
}

data class EvaluationResult(
    val actionName: String,
    val metrics: EvaluationMetrics,
    val predictions: List<SamplePrediction>,
    val squatThreshold: SquatThreshold? = null,
    val pushUpThreshold: PushUpThreshold? = null,
)

data class ThresholdOptimizationResult(
    val actionName: String,
    val baseline: EvaluationResult,
    val optimized: EvaluationResult,
    val squatThreshold: SquatThreshold? = null,
    val pushUpThreshold: PushUpThreshold? = null,
)
