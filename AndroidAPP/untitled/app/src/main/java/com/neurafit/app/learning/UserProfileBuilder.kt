package com.neurafit.app.learning

import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.data.TrainingRecord

object UserProfileBuilder {
    private const val RECENT_RECORD_LIMIT = 7
    private const val TREND_WINDOW_SIZE = 3
    private const val TREND_SCORE_DELTA = 5.0

    fun build(
        records: List<TrainingRecord>,
        actionName: String? = null,
    ): UserActionProfile? {
        val recentRecords = records
            .asSequence()
            .filter { actionName == null || it.actionName == actionName }
            .sortedByDescending(TrainingRecord::timestamp)
            .take(RECENT_RECORD_LIMIT)
            .toList()

        if (recentRecords.isEmpty()) {
            return null
        }

        val totalReps = recentRecords.sumOf(TrainingRecord::count).coerceAtLeast(1)
        val profileActionName = actionName ?: recentRecords.first().actionName
        val errorCounts = errorCounts(recentRecords, profileActionName)
        val errorRates = errorCounts.associate { (type, count) ->
            type to count.toDouble() / totalReps
        }

        return UserActionProfile(
            actionName = profileActionName,
            averageScore = recentRecords.map(TrainingRecord::score).average(),
            mainError = mainError(errorCounts),
            improvementTrend = improvementTrend(recentRecords),
            squatTooShallowRate = errorRates[ActionErrorType.SQUAT_TOO_SHALLOW] ?: 0.0,
            backLeanRate = errorRates[ActionErrorType.BACK_LEAN_TOO_MUCH] ?: 0.0,
            kneeValgusRate = errorRates[ActionErrorType.KNEE_VALGUS] ?: 0.0,
            errorRates = errorRates,
        )
    }

    private fun errorCounts(
        records: List<TrainingRecord>,
        actionName: String,
    ): List<Pair<ActionErrorType, Int>> =
        when (actionName) {
            WorkoutActions.PUSH_UP -> listOf(
                ActionErrorType.PUSH_UP_TOO_SHALLOW to records.sumOf(TrainingRecord::pushUpTooShallowCount),
                ActionErrorType.WAIST_COLLAPSE to records.sumOf(TrainingRecord::waistCollapseCount),
                ActionErrorType.HIPS_TOO_HIGH to records.sumOf(TrainingRecord::hipsTooHighCount),
                ActionErrorType.PUSH_UP_TOO_FAST to records.sumOf(TrainingRecord::pushUpTooFastCount),
            )
            else -> listOf(
                ActionErrorType.SQUAT_TOO_SHALLOW to records.sumOf(TrainingRecord::squatTooShallowCount),
                ActionErrorType.BACK_LEAN_TOO_MUCH to records.sumOf(TrainingRecord::backLeanTooMuchCount),
                ActionErrorType.KNEE_VALGUS to records.sumOf(TrainingRecord::kneeValgusCount),
            )
        }

    private fun mainError(errors: List<Pair<ActionErrorType, Int>>): ActionErrorType {
        val mainError = errors.maxByOrNull { it.second }
        return if (mainError == null || mainError.second == 0) {
            ActionErrorType.NONE
        } else {
            mainError.first
        }
    }

    private fun improvementTrend(records: List<TrainingRecord>): String {
        if (records.size < TREND_WINDOW_SIZE * 2) {
            return "基本稳定"
        }

        val recentAverage = records
            .take(TREND_WINDOW_SIZE)
            .map(TrainingRecord::score)
            .average()
        val previousAverage = records
            .drop(TREND_WINDOW_SIZE)
            .take(TREND_WINDOW_SIZE)
            .map(TrainingRecord::score)
            .average()
        val delta = recentAverage - previousAverage

        return when {
            delta > TREND_SCORE_DELTA -> "进步明显"
            delta < -TREND_SCORE_DELTA -> "状态下降"
            else -> "基本稳定"
        }
    }
}
