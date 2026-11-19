package com.neurafit.app.analysis

data class CorrectionResult(
    val score: Int,
    val errors: Set<ActionErrorType>,
    val message: String,
)
