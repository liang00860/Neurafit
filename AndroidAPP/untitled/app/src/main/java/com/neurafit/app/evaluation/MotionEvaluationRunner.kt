package com.neurafit.app.evaluation

import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.PushUpAnalyzer
import com.neurafit.app.analysis.PushUpThreshold
import com.neurafit.app.analysis.SquatAnalyzer
import com.neurafit.app.analysis.SquatThreshold
import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.dataset.MotionDatasetSample

object MotionEvaluationRunner {
    fun evaluate(
        samples: List<MotionDatasetSample>,
        actionName: String,
        squatThreshold: SquatThreshold = SquatThreshold(),
        pushUpThreshold: PushUpThreshold = PushUpThreshold(),
    ): EvaluationResult {
        val filteredSamples = samples.filter { it.actionName == actionName }
        val predictions = filteredSamples.map { sample ->
            when (actionName) {
                WorkoutActions.PUSH_UP -> predictPushUp(sample, pushUpThreshold)
                else -> predictSquat(sample, squatThreshold)
            }
        }
        return EvaluationResult(
            actionName = actionName,
            metrics = calculateMetrics(predictions),
            predictions = predictions,
            squatThreshold = if (actionName == WorkoutActions.SQUAT) squatThreshold else null,
            pushUpThreshold = if (actionName == WorkoutActions.PUSH_UP) pushUpThreshold else null,
        )
    }

    private fun predictSquat(
        sample: MotionDatasetSample,
        threshold: SquatThreshold,
    ): SamplePrediction {
        val result = SquatAnalyzer.analyze(
            features = sample.toBodyFeatures(),
            squatState = sample.toSquatState(),
            threshold = threshold,
        )
        return SamplePrediction(
            sampleId = sample.sampleId,
            expectedErrors = sample.expectedErrors,
            predictedErrors = result.errors,
            score = result.score,
        )
    }

    private fun predictPushUp(
        sample: MotionDatasetSample,
        threshold: PushUpThreshold,
    ): SamplePrediction {
        val result = PushUpAnalyzer.analyze(
            features = sample.toPushUpFeatures(),
            pushUpState = sample.toPushUpState(),
            threshold = threshold,
        )
        return SamplePrediction(
            sampleId = sample.sampleId,
            expectedErrors = sample.expectedErrors,
            predictedErrors = result.errors,
            score = result.score,
        )
    }

    private fun calculateMetrics(predictions: List<SamplePrediction>): EvaluationMetrics {
        val exactMatchCount = predictions.count(SamplePrediction::isExactMatch)
        val errorTypes = predictions
            .flatMap { it.expectedErrors + it.predictedErrors }
            .filter { it != ActionErrorType.NONE }
            .toSet()

        val perError = errorTypes.associateWith { errorType ->
            val truePositive = predictions.count {
                errorType in it.expectedErrors && errorType in it.predictedErrors
            }
            val falsePositive = predictions.count {
                errorType !in it.expectedErrors && errorType in it.predictedErrors
            }
            val falseNegative = predictions.count {
                errorType in it.expectedErrors && errorType !in it.predictedErrors
            }
            ErrorMetric(
                truePositive = truePositive,
                falsePositive = falsePositive,
                falseNegative = falseNegative,
            )
        }

        return EvaluationMetrics(
            sampleCount = predictions.size,
            exactMatchCount = exactMatchCount,
            exactMatchAccuracy = exactMatchCount.toDouble() / predictions.size.coerceAtLeast(1),
            perError = perError,
        )
    }
}
