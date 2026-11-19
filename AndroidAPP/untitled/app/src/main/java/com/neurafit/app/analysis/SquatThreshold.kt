package com.neurafit.app.analysis

data class SquatThreshold(
    val maxKneeAngleAtBottom: Double = 110.0,
    val maxBackLeanAngle: Double = 35.0,
    val minKneeDistance: Double = 0.08,
)
