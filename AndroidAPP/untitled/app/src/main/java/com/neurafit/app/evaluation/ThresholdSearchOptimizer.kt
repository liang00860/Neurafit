package com.neurafit.app.evaluation

import com.neurafit.app.analysis.PushUpThreshold
import com.neurafit.app.analysis.SquatThreshold
import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.dataset.MotionDatasetSample

object ThresholdSearchOptimizer {
    fun optimize(
        samples: List<MotionDatasetSample>,
        actionName: String,
    ): ThresholdOptimizationResult =
        when (actionName) {
            WorkoutActions.PUSH_UP -> optimizePushUp(samples)
            else -> optimizeSquat(samples)
        }

    fun optimizeSquat(samples: List<MotionDatasetSample>): ThresholdOptimizationResult {
        val baselineThreshold = SquatThreshold()
        val baseline = MotionEvaluationRunner.evaluate(
            samples = samples,
            actionName = WorkoutActions.SQUAT,
            squatThreshold = baselineThreshold,
        )
        var bestThreshold = baselineThreshold
        var bestResult = baseline

        for (kneeAngle in 100..130 step 5) {
            for (backLean in 30..50 step 5) {
                for (kneeDistanceStep in 5..12) {
                    val threshold = SquatThreshold(
                        maxKneeAngleAtBottom = kneeAngle.toDouble(),
                        maxBackLeanAngle = backLean.toDouble(),
                        minKneeDistance = kneeDistanceStep / 100.0,
                    )
                    val result = MotionEvaluationRunner.evaluate(
                        samples = samples,
                        actionName = WorkoutActions.SQUAT,
                        squatThreshold = threshold,
                    )
                    if (result.isBetterThan(bestResult)) {
                        bestThreshold = threshold
                        bestResult = result
                    }
                }
            }
        }

        return ThresholdOptimizationResult(
            actionName = WorkoutActions.SQUAT,
            baseline = baseline,
            optimized = bestResult,
            squatThreshold = bestThreshold,
        )
    }

    fun optimizePushUp(samples: List<MotionDatasetSample>): ThresholdOptimizationResult {
        val baselineThreshold = PushUpThreshold()
        val baseline = MotionEvaluationRunner.evaluate(
            samples = samples,
            actionName = WorkoutActions.PUSH_UP,
            pushUpThreshold = baselineThreshold,
        )
        var bestThreshold = baselineThreshold
        var bestResult = baseline

        for (elbowAngle in 85..120 step 5) {
            for (bodyLineAngle in 150..175 step 5) {
                for (descentStep in 4..14) {
                    val threshold = PushUpThreshold(
                        maxElbowAngleAtBottom = elbowAngle.toDouble(),
                        minBodyLineAngle = bodyLineAngle.toDouble(),
                        minDescent = descentStep / 100.0,
                    )
                    val result = MotionEvaluationRunner.evaluate(
                        samples = samples,
                        actionName = WorkoutActions.PUSH_UP,
                        pushUpThreshold = threshold,
                    )
                    if (result.isBetterThan(bestResult)) {
                        bestThreshold = threshold
                        bestResult = result
                    }
                }
            }
        }

        return ThresholdOptimizationResult(
            actionName = WorkoutActions.PUSH_UP,
            baseline = baseline,
            optimized = bestResult,
            pushUpThreshold = bestThreshold,
        )
    }

    private fun EvaluationResult.isBetterThan(other: EvaluationResult): Boolean {
        val accuracyDelta = metrics.exactMatchAccuracy - other.metrics.exactMatchAccuracy
        if (kotlin.math.abs(accuracyDelta) > MIN_SIGNIFICANT_DELTA) {
            return accuracyDelta > 0
        }

        val f1 = metrics.perError.values.map(ErrorMetric::f1).averageOrZero()
        val otherF1 = other.metrics.perError.values.map(ErrorMetric::f1).averageOrZero()
        return f1 > otherF1
    }

    private fun List<Double>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else average()

    private const val MIN_SIGNIFICANT_DELTA = 0.0001
}
