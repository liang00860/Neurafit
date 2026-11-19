package com.neurafit.app.data

import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.BodyFeatures
import com.neurafit.app.analysis.CorrectionResult
import com.neurafit.app.analysis.SquatCounterSnapshot
import com.neurafit.app.analysis.SquatStage
import kotlin.math.roundToInt

class TrainingSessionStats(
    private val actionName: String,
    private val startedAt: Long = System.currentTimeMillis(),
) {
    val startedAtMs: Long
        get() = startedAt

    private var savedRepCount = 0
    private var kneeAngleSum = 0.0
    private var kneeAngleFrameCount = 0
    private var backLeanAngleSum = 0.0
    private var backLeanFrameCount = 0
    private var squatTooShallowCount = 0
    private var backLeanTooMuchCount = 0
    private var kneeValgusCount = 0
    private var scoreSum = 0
    private var scoredRepCount = 0
    private var currentRepMaxBackLeanAngle: Double? = null
    private val currentRepErrors = linkedSetOf<ActionErrorType>()

    fun onFrame(
        features: BodyFeatures?,
        correctionResult: CorrectionResult,
        squatState: SquatCounterSnapshot,
    ) {
        features?.averageKneeAngle()?.let { kneeAngle ->
            kneeAngleSum += kneeAngle
            kneeAngleFrameCount += 1
        }
        features?.torsoTiltAngle?.let { backLeanAngle ->
            backLeanAngleSum += backLeanAngle
            backLeanFrameCount += 1
            if (squatState.isInRep()) {
                currentRepMaxBackLeanAngle = maxOf(currentRepMaxBackLeanAngle ?: backLeanAngle, backLeanAngle)
            }
        }

        if (squatState.isInRep()) {
            currentRepErrors += correctionResult.actionErrors()
        }
    }

    fun createRepRecordIfCompleted(
        trainingId: Long,
        squatState: SquatCounterSnapshot,
        correctionResult: CorrectionResult,
    ): RepRecord? {
        if (squatState.repetitionCount <= savedRepCount) {
            return null
        }

        val repIndex = squatState.repetitionCount
        val errors = (currentRepErrors + correctionResult.actionErrors()).toSet()
        val score = scoreFor(errors)
        countRepErrors(errors)
        scoreSum += score
        scoredRepCount += 1
        savedRepCount = repIndex

        val record = RepRecord(
            trainingId = trainingId,
            actionName = actionName,
            repIndex = repIndex,
            score = score,
            minKneeAngle = squatState.lastRepMinimumKneeAngle,
            maxBackLeanAngle = currentRepMaxBackLeanAngle,
            errorTypes = errors.serializeErrors(),
            timestamp = System.currentTimeMillis(),
        )

        currentRepErrors.clear()
        currentRepMaxBackLeanAngle = null
        return record
    }

    fun buildTrainingRecord(
        trainingId: Long,
        squatState: SquatCounterSnapshot,
        fallbackScore: Int,
        durationMs: Long,
    ): TrainingRecord =
        TrainingRecord(
            id = trainingId,
            actionName = actionName,
            timestamp = startedAt,
            durationMs = durationMs,
            count = squatState.repetitionCount,
            score = if (scoredRepCount > 0) (scoreSum.toDouble() / scoredRepCount).roundToInt() else fallbackScore,
            squatTooShallowCount = squatTooShallowCount,
            backLeanTooMuchCount = backLeanTooMuchCount,
            kneeValgusCount = kneeValgusCount,
            avgKneeAngle = average(kneeAngleSum, kneeAngleFrameCount),
            avgBackLeanAngle = average(backLeanAngleSum, backLeanFrameCount),
        )

    private fun SquatCounterSnapshot.isInRep(): Boolean =
        stage != SquatStage.STANDING || currentRepMinimumKneeAngle != null || maxHipDepthInCurrentRep != null

    private fun BodyFeatures.averageKneeAngle(): Double? =
        when {
            leftKneeAngle != null && rightKneeAngle != null -> (leftKneeAngle + rightKneeAngle) / 2.0
            leftKneeAngle != null -> leftKneeAngle
            rightKneeAngle != null -> rightKneeAngle
            else -> null
        }

    private fun CorrectionResult.actionErrors(): Set<ActionErrorType> =
        errors.filterTo(linkedSetOf()) { it != ActionErrorType.NONE }

    private fun countRepErrors(errors: Set<ActionErrorType>) {
        if (ActionErrorType.SQUAT_TOO_SHALLOW in errors) {
            squatTooShallowCount += 1
        }
        if (ActionErrorType.BACK_LEAN_TOO_MUCH in errors) {
            backLeanTooMuchCount += 1
        }
        if (ActionErrorType.KNEE_VALGUS in errors) {
            kneeValgusCount += 1
        }
    }

    private fun scoreFor(errors: Set<ActionErrorType>): Int {
        val deduction = errors.sumOf { error ->
            when (error) {
                ActionErrorType.NONE -> 0
                ActionErrorType.SQUAT_TOO_SHALLOW -> 25
                ActionErrorType.BACK_LEAN_TOO_MUCH -> 20
                ActionErrorType.KNEE_VALGUS -> 25
                ActionErrorType.PUSH_UP_TOO_SHALLOW,
                ActionErrorType.WAIST_COLLAPSE,
                ActionErrorType.HIPS_TOO_HIGH,
                ActionErrorType.PUSH_UP_TOO_FAST -> 0
            }
        }
        return (100 - deduction).coerceIn(0, 100)
    }

    private fun Set<ActionErrorType>.serializeErrors(): String =
        if (isEmpty()) {
            ActionErrorType.NONE.name
        } else {
            joinToString(separator = ",") { it.name }
        }

    private fun average(sum: Double, count: Int): Double? =
        if (count > 0) sum / count else null
}
