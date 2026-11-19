package com.neurafit.app.learning

import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.data.TrainingRecord
import com.neurafit.app.data.UserActionThreshold

object ThresholdUpdater {
    private const val RECENT_RECORD_LIMIT = 7
    private const val MIN_RECORDS_TO_UPDATE = 3
    private const val HIGH_SCORE = 85.0
    private const val LOW_SCORE = 60.0
    private const val KNEE_STEP = 3.0
    private const val BACK_STEP = 2.0
    private const val MIN_KNEE_ANGLE_AT_BOTTOM = 100.0
    private const val MAX_KNEE_ANGLE_AT_BOTTOM = 130.0
    private const val MIN_BACK_LEAN_ANGLE = 30.0
    private const val MAX_BACK_LEAN_ANGLE = 50.0
    private const val ELBOW_STEP = 3.0
    private const val BODY_LINE_STEP = 2.0
    private const val DESCENT_STEP = 0.01
    private const val MIN_PUSH_UP_ELBOW_AT_BOTTOM = 80.0
    private const val MAX_PUSH_UP_ELBOW_AT_BOTTOM = 120.0
    private const val MIN_PUSH_UP_BODY_LINE = 150.0
    private const val MAX_PUSH_UP_BODY_LINE = 175.0
    private const val MIN_PUSH_UP_DESCENT = 0.04
    private const val MAX_PUSH_UP_DESCENT = 0.16

    fun update(
        actionName: String,
        records: List<TrainingRecord>,
        currentThreshold: UserActionThreshold?,
        now: Long = System.currentTimeMillis(),
    ): UserActionThreshold {
        val current = currentThreshold ?: UserActionThreshold.defaultFor(actionName, updatedAt = now)
        val recentRecords = records
            .asSequence()
            .filter { it.actionName == actionName }
            .sortedByDescending(TrainingRecord::timestamp)
            .take(RECENT_RECORD_LIMIT)
            .toList()

        if (recentRecords.size < MIN_RECORDS_TO_UPDATE) {
            return current.copy(updatedAt = now).coerceToAllowedRange()
        }

        val averageScore = recentRecords.map(TrainingRecord::score).average()
        val adjusted = when (actionName) {
            WorkoutActions.PUSH_UP -> adjustPushUpThreshold(current, averageScore, now)
            else -> adjustSquatThreshold(current, averageScore, now)
        }

        return adjusted.coerceToAllowedRange()
    }

    private fun adjustSquatThreshold(
        current: UserActionThreshold,
        averageScore: Double,
        now: Long,
    ): UserActionThreshold =
        when {
            averageScore > HIGH_SCORE -> current.copy(
                maxKneeAngleAtBottom = current.maxKneeAngleAtBottom - KNEE_STEP,
                maxBackLeanAngle = current.maxBackLeanAngle - BACK_STEP,
                updatedAt = now,
            )
            averageScore < LOW_SCORE -> current.copy(
                maxKneeAngleAtBottom = current.maxKneeAngleAtBottom + KNEE_STEP,
                maxBackLeanAngle = current.maxBackLeanAngle + BACK_STEP,
                updatedAt = now,
            )
            else -> current.copy(updatedAt = now)
        }

    private fun adjustPushUpThreshold(
        current: UserActionThreshold,
        averageScore: Double,
        now: Long,
    ): UserActionThreshold =
        when {
            averageScore > HIGH_SCORE -> current.copy(
                pushUpMaxElbowAngleAtBottom = current.pushUpMaxElbowAngleAtBottom - ELBOW_STEP,
                pushUpMinBodyLineAngle = current.pushUpMinBodyLineAngle + BODY_LINE_STEP,
                pushUpMinDescent = current.pushUpMinDescent + DESCENT_STEP,
                updatedAt = now,
            )
            averageScore < LOW_SCORE -> current.copy(
                pushUpMaxElbowAngleAtBottom = current.pushUpMaxElbowAngleAtBottom + ELBOW_STEP,
                pushUpMinBodyLineAngle = current.pushUpMinBodyLineAngle - BODY_LINE_STEP,
                pushUpMinDescent = current.pushUpMinDescent - DESCENT_STEP,
                updatedAt = now,
            )
            else -> current.copy(updatedAt = now)
        }

    private fun UserActionThreshold.coerceToAllowedRange(): UserActionThreshold =
        copy(
            maxKneeAngleAtBottom = maxKneeAngleAtBottom.coerceIn(
                MIN_KNEE_ANGLE_AT_BOTTOM,
                MAX_KNEE_ANGLE_AT_BOTTOM,
            ),
            maxBackLeanAngle = maxBackLeanAngle.coerceIn(
                MIN_BACK_LEAN_ANGLE,
                MAX_BACK_LEAN_ANGLE,
            ),
            pushUpMaxElbowAngleAtBottom = pushUpMaxElbowAngleAtBottom.coerceIn(
                MIN_PUSH_UP_ELBOW_AT_BOTTOM,
                MAX_PUSH_UP_ELBOW_AT_BOTTOM,
            ),
            pushUpMinBodyLineAngle = pushUpMinBodyLineAngle.coerceIn(
                MIN_PUSH_UP_BODY_LINE,
                MAX_PUSH_UP_BODY_LINE,
            ),
            pushUpMinDescent = pushUpMinDescent.coerceIn(
                MIN_PUSH_UP_DESCENT,
                MAX_PUSH_UP_DESCENT,
            ),
        )
}
