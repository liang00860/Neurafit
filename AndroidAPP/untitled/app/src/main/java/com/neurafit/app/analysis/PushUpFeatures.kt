package com.neurafit.app.analysis

data class PushUpFeatures(
    val leftElbowAngle: Double?,
    val rightElbowAngle: Double?,
    val bodyLineAngle: Double?,
    val bodyDescent: Double?,
    val shoulderY: Double?,
    val hipY: Double?,
    val ankleY: Double?,
    val hipOffsetFromBodyLine: Double?,
) {
    fun averageElbowAngle(): Double? =
        when {
            leftElbowAngle != null && rightElbowAngle != null -> (leftElbowAngle + rightElbowAngle) / 2.0
            leftElbowAngle != null -> leftElbowAngle
            rightElbowAngle != null -> rightElbowAngle
            else -> null
        }
}

object PushUpFeatureExtractor {
    fun extract(
        features: BodyFeatures?,
        referenceShoulderY: Double? = null,
    ): PushUpFeatures? {
        if (features == null) return null

        val shoulder = features.shoulderCenter
        val hip = features.hipCenter
        val ankle = features.ankleCenter
        val shoulderY = shoulder?.y
        val bodyDescent = if (shoulderY != null && referenceShoulderY != null) {
            shoulderY - referenceShoulderY
        } else {
            null
        }

        return PushUpFeatures(
            leftElbowAngle = features.leftElbowAngle,
            rightElbowAngle = features.rightElbowAngle,
            bodyLineAngle = features.bodyLineAngle,
            bodyDescent = bodyDescent,
            shoulderY = shoulderY,
            hipY = hip?.y,
            ankleY = ankle?.y,
            hipOffsetFromBodyLine = calculateHipOffsetFromBodyLine(
                shoulder = shoulder,
                hip = hip,
                ankle = ankle,
            ),
        )
    }

    private fun calculateHipOffsetFromBodyLine(
        shoulder: Point2D?,
        hip: Point2D?,
        ankle: Point2D?,
    ): Double? {
        if (shoulder == null || hip == null || ankle == null) return null

        val t = if (kotlin.math.abs(ankle.x - shoulder.x) > MIN_AXIS_DELTA) {
            ((hip.x - shoulder.x) / (ankle.x - shoulder.x)).coerceIn(0.0, 1.0)
        } else {
            0.5
        }
        val expectedHipY = shoulder.y + (ankle.y - shoulder.y) * t
        return hip.y - expectedHipY
    }

    private const val MIN_AXIS_DELTA = 0.0001
}
