package com.neurafit.app.analysis

data class PushUpThreshold(
    val maxElbowAngleAtBottom: Double = 105.0,
    val minBodyLineAngle: Double = 160.0,
    val minDescent: Double = 0.06,
    val maxWaistSag: Double = 0.05,
    val maxHipPike: Double = 0.05,
    val minRepDurationMs: Long = 900L,
)
