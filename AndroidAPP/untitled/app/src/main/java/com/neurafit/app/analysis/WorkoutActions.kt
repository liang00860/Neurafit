package com.neurafit.app.analysis

object WorkoutActions {
    const val SQUAT = "SQUAT"
    const val PUSH_UP = "PUSH_UP"

    fun displayName(actionName: String): String =
        when (actionName) {
            SQUAT -> "深蹲"
            PUSH_UP -> "俯卧撑"
            else -> actionName
        }
}
