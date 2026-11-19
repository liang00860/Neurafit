package com.neurafit.app.data

import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.CorrectionResult
import com.neurafit.app.analysis.PushUpCounterSnapshot
import com.neurafit.app.analysis.PushUpFeatures
import com.neurafit.app.analysis.PushUpStage
import kotlin.math.roundToInt

class PushUpTrainingSessionStats(
    private val actionName: String,
    private val startedAt: Long = System.currentTimeMillis(),
) {
    val startedAtMs: Long
        get() = startedAt

    private var savedRepCount = 0
    private var elbowAngleSum = 0.0
    private var elbowAngleFrameCount = 0
    private var bodyLineAngleSum = 0.0
    private var bodyLineAngleFrameCount = 0
    private var bodyDescentSum = 0.0
    private var bodyDescentFrameCount = 0
    private var pushUpTooShallowCount = 0
    private var waistCollapseCount = 0
    private var hipsTooHighCount = 0
    private var pushUpTooFastCount = 0
    private var scoreSum = 0
    private var scoredRepCount = 0
    private var currentRepMinBodyLineAngle: Double? = null
    private val currentRepErrors = linkedSetOf<ActionErrorType>()

    fun onFrame(
        features: PushUpFeatures?,
        correctionResult: CorrectionResult,
        pushUpState: PushUpCounterSnapshot,
    ) {
        features?.averageElbowAngle()?.let { angle ->
            elbowAngleSum += angle
            elbowAngleFrameCount += 1
        }
        features?.bodyLineAngle?.let { angle ->
            bodyLineAngleSum += angle
            bodyLineAngleFrameCount += 1
            if (pushUpState.isInRep()) {
                currentRepMinBodyLineAngle = minOf(currentRepMinBodyLineAngle ?: angle, angle)
            }
        }
        pushUpState.currentBodyDescent?.let { descent ->
            bodyDescentSum += descent
            bodyDescentFrameCount += 1
        }

        if (pushUpState.isInRep()) {
            currentRepErrors += correctionResult.actionErrors()
        }
    }

    fun createRepRecordIfCompleted(
        trainingId: Long,
        pushUpState: PushUpCounterSnapshot,
        correctionResult: CorrectionResult,
    ): RepRecord? {
        if (pushUpState.repetitionCount <= savedRepCount) {
            return null
        }

        val repIndex = pushUpState.repetitionCount
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
            minKneeAngle = null,
            maxBackLeanAngle = null,
            errorTypes = errors.serializeErrors(),
            timestamp = System.currentTimeMillis(),
            minElbowAngle = pushUpState.lastRepMinimumElbowAngle,
            maxBodyDescent = pushUpState.lastRepMaxBodyDescent,
            minBodyLineAngle = currentRepMinBodyLineAngle,
            durationMs = pushUpState.lastRepDurationMs,
        )

        currentRepErrors.clear()
        currentRepMinBodyLineAngle = null
        return record
    }

    fun buildTrainingRecord(
        trainingId: Long,
        pushUpState: PushUpCounterSnapshot,
        fallbackScore: Int,
        durationMs: Long,
    ): TrainingRecord =
        TrainingRecord(
            id = trainingId,
            actionName = actionName,
            timestamp = startedAt,
            durationMs = durationMs,
            count = pushUpState.repetitionCount,
            score = if (scoredRepCount > 0) (scoreSum.toDouble() / scoredRepCount).roundToInt() else fallbackScore,
            squatTooShallowCount = 0,
            backLeanTooMuchCount = 0,
            kneeValgusCount = 0,
            avgKneeAngle = null,
            avgBackLeanAngle = null,
            pushUpTooShallowCount = pushUpTooShallowCount,
            waistCollapseCount = waistCollapseCount,
            hipsTooHighCount = hipsTooHighCount,
            pushUpTooFastCount = pushUpTooFastCount,
            avgElbowAngle = average(elbowAngleSum, elbowAngleFrameCount),
            avgBodyLineAngle = average(bodyLineAngleSum, bodyLineAngleFrameCount),
            avgBodyDescent = average(bodyDescentSum, bodyDescentFrameCount),
        )

    private fun PushUpCounterSnapshot.isInRep(): Boolean =
        stage != PushUpStage.ARMS_STRAIGHT ||
            currentRepMinimumElbowAngle != null ||
            maxBodyDescentInCurrentRep != null

    private fun CorrectionResult.actionErrors(): Set<ActionErrorType> =
        errors.filterTo(linkedSetOf()) { it != ActionErrorType.NONE }

    private fun countRepErrors(errors: Set<ActionErrorType>) {
        if (ActionErrorType.PUSH_UP_TOO_SHALLOW in errors) {
            pushUpTooShallowCount += 1
        }
        if (ActionErrorType.WAIST_COLLAPSE in errors) {
            waistCollapseCount += 1
        }
        if (ActionErrorType.HIPS_TOO_HIGH in errors) {
            hipsTooHighCount += 1
        }
        if (ActionErrorType.PUSH_UP_TOO_FAST in errors) {
            pushUpTooFastCount += 1
        }
    }

    private fun scoreFor(errors: Set<ActionErrorType>): Int {
        val deduction = errors.sumOf { error ->
            when (error) {
                ActionErrorType.NONE -> 0
                ActionErrorType.PUSH_UP_TOO_SHALLOW -> 25
                ActionErrorType.WAIST_COLLAPSE -> 25
                ActionErrorType.HIPS_TOO_HIGH -> 20
                ActionErrorType.PUSH_UP_TOO_FAST -> 15
                ActionErrorType.SQUAT_TOO_SHALLOW,
                ActionErrorType.BACK_LEAN_TOO_MUCH,
                ActionErrorType.KNEE_VALGUS -> 0
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
