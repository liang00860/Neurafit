package com.neurafit.app.dataset

import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.BodyFeatures
import com.neurafit.app.analysis.PushUpCounterSnapshot
import com.neurafit.app.analysis.PushUpFeatures
import com.neurafit.app.analysis.PushUpStage
import com.neurafit.app.analysis.SquatCounterSnapshot
import com.neurafit.app.analysis.SquatStage
import com.neurafit.app.analysis.WorkoutActions

data class MotionDatasetSample(
    val sampleId: String,
    val actionName: String,
    val expectedCount: Int? = null,
    val expectedErrors: Set<ActionErrorType> = setOf(ActionErrorType.NONE),
    val leftKneeAngle: Double? = null,
    val rightKneeAngle: Double? = null,
    val torsoTiltAngle: Double? = null,
    val leftRightKneeDistance: Double? = null,
    val leftElbowAngle: Double? = null,
    val rightElbowAngle: Double? = null,
    val bodyLineAngle: Double? = null,
    val bodyDescent: Double? = null,
    val hipOffsetFromBodyLine: Double? = null,
    val repDurationMs: Long? = null,
) {
    fun toBodyFeatures(): BodyFeatures =
        BodyFeatures(
            leftElbowAngle = leftElbowAngle,
            rightElbowAngle = rightElbowAngle,
            leftShoulderAngle = null,
            rightShoulderAngle = null,
            leftKneeAngle = leftKneeAngle,
            rightKneeAngle = rightKneeAngle,
            leftHipAngle = null,
            rightHipAngle = null,
            leftAnkleAngle = null,
            rightAnkleAngle = null,
            shoulderCenter = null,
            hipCenter = null,
            kneeCenter = null,
            ankleCenter = null,
            torsoTiltAngle = torsoTiltAngle,
            bodyLineAngle = bodyLineAngle,
            shoulderHipDistance = null,
            hipKneeDistance = null,
            kneeAnkleDistance = null,
            leftRightKneeDistance = leftRightKneeDistance,
            shoulderHeightDiff = null,
            hipHeightDiff = null,
            kneeHeightDiff = null,
            ankleHeightDiff = null,
            kneeAngleDiff = diff(leftKneeAngle, rightKneeAngle),
            elbowAngleDiff = diff(leftElbowAngle, rightElbowAngle),
            hipAngleDiff = null,
        )

    fun toSquatState(): SquatCounterSnapshot {
        val kneeAngle = average(leftKneeAngle, rightKneeAngle)
        return SquatCounterSnapshot(
            repetitionCount = expectedCount ?: 0,
            stage = if (actionName == WorkoutActions.SQUAT) SquatStage.BOTTOM else SquatStage.STANDING,
            currentKneeAngle = kneeAngle,
            rawKneeAngle = kneeAngle,
            kneeAngleSource = "dataset",
            currentHipDepth = null,
            maxHipDepthInCurrentRep = null,
            currentRepMinimumKneeAngle = kneeAngle,
            lastRepMinimumKneeAngle = null,
            repMinimumKneeAngles = listOfNotNull(kneeAngle),
        )
    }

    fun toPushUpFeatures(): PushUpFeatures =
        PushUpFeatures(
            leftElbowAngle = leftElbowAngle,
            rightElbowAngle = rightElbowAngle,
            bodyLineAngle = bodyLineAngle,
            bodyDescent = bodyDescent,
            shoulderY = null,
            hipY = null,
            ankleY = null,
            hipOffsetFromBodyLine = hipOffsetFromBodyLine,
        )

    fun toPushUpState(): PushUpCounterSnapshot {
        val elbowAngle = average(leftElbowAngle, rightElbowAngle)
        return PushUpCounterSnapshot(
            repetitionCount = expectedCount ?: 0,
            stage = if (actionName == WorkoutActions.PUSH_UP) PushUpStage.BOTTOM else PushUpStage.ARMS_STRAIGHT,
            currentElbowAngle = elbowAngle,
            rawElbowAngle = elbowAngle,
            currentBodyDescent = bodyDescent,
            maxBodyDescentInCurrentRep = bodyDescent,
            currentRepMinimumElbowAngle = elbowAngle,
            lastRepMinimumElbowAngle = null,
            lastRepMaxBodyDescent = bodyDescent,
            currentRepDurationMs = repDurationMs,
            lastRepDurationMs = repDurationMs,
            lastRepTooFast = repDurationMs != null && repDurationMs < DEFAULT_MIN_PUSH_UP_DURATION_MS,
            repMinimumElbowAngles = listOfNotNull(elbowAngle),
        )
    }

    private fun average(first: Double?, second: Double?): Double? =
        when {
            first != null && second != null -> (first + second) / 2.0
            first != null -> first
            second != null -> second
            else -> null
        }

    private fun diff(first: Double?, second: Double?): Double? =
        if (first != null && second != null) kotlin.math.abs(first - second) else null

    private companion object {
        const val DEFAULT_MIN_PUSH_UP_DURATION_MS = 900L
    }
}
