package com.neurafit.app.learning

import com.neurafit.app.analysis.ActionErrorType

data class UserActionProfile(
    val actionName: String,
    val averageScore: Double,
    val mainError: ActionErrorType,
    val improvementTrend: String,
    val squatTooShallowRate: Double,
    val backLeanRate: Double,
    val kneeValgusRate: Double,
    val errorRates: Map<ActionErrorType, Double> = emptyMap(),
)
