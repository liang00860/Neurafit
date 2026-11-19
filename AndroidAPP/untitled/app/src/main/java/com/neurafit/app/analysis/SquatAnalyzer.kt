package com.neurafit.app.analysis

import kotlin.math.roundToInt

object SquatAnalyzer {
    fun analyze(
        features: BodyFeatures?,
        squatState: SquatCounterSnapshot? = null,
        threshold: SquatThreshold = SquatThreshold(),
    ): CorrectionResult {
        if (features == null) {
            return CorrectionResult(
                score = 0,
                errors = setOf(ActionErrorType.NONE),
                message = "未检测到完整姿态，请调整站位，让全身进入画面。",
            )
        }

        val errors = linkedSetOf<ActionErrorType>()
        val kneeAngleAtBottom = squatState?.currentRepMinimumKneeAngle
            ?: squatState?.lastRepMinimumKneeAngle
            ?: features.averageKneeAngle()
        val shouldEvaluateDepth = squatState == null ||
            squatState.stage != SquatStage.STANDING ||
            squatState.lastRepMinimumKneeAngle != null

        if (
            shouldEvaluateDepth &&
            kneeAngleAtBottom != null &&
            kneeAngleAtBottom > threshold.maxKneeAngleAtBottom
        ) {
            errors += ActionErrorType.SQUAT_TOO_SHALLOW
        }

        val torsoTiltAngle = features.torsoTiltAngle
        if (torsoTiltAngle != null && torsoTiltAngle > threshold.maxBackLeanAngle) {
            errors += ActionErrorType.BACK_LEAN_TOO_MUCH
        }

        val kneeDistance = features.leftRightKneeDistance
        if (kneeDistance != null && kneeDistance < threshold.minKneeDistance) {
            errors += ActionErrorType.KNEE_VALGUS
        }

        val score = calculateScore(errors)
        val visibleErrors = if (errors.isEmpty()) {
            setOf(ActionErrorType.NONE)
        } else {
            errors
        }

        return CorrectionResult(
            score = score,
            errors = visibleErrors,
            message = buildMessage(visibleErrors),
        )
    }

    private fun calculateScore(errors: Set<ActionErrorType>): Int {
        val deduction = errors.sumOf { error ->
            when (error) {
                ActionErrorType.NONE -> 0
                ActionErrorType.SQUAT_TOO_SHALLOW -> 25
                ActionErrorType.BACK_LEAN_TOO_MUCH -> 20
                ActionErrorType.KNEE_VALGUS -> 25
                ActionErrorType.PUSH_UP_TOO_SHALLOW,
                ActionErrorType.WAIST_COLLAPSE,
                ActionErrorType.HIPS_TOO_HIGH,
                ActionErrorType.PUSH_UP_TOO_FAST -> 0
            }
        }
        return (100 - deduction).coerceIn(0, 100)
    }

    private fun buildMessage(errors: Set<ActionErrorType>): String {
        if (errors.contains(ActionErrorType.NONE)) {
            return "动作不错，继续保持膝盖稳定和背部控制。"
        }

        return errors.joinToString(separator = "\n") { error ->
            when (error) {
                ActionErrorType.NONE -> "动作不错，继续保持。"
                ActionErrorType.SQUAT_TOO_SHALLOW -> "下蹲深度不足，继续向下坐，让大腿更接近平行地面。"
                ActionErrorType.BACK_LEAN_TOO_MUCH -> "背部前倾过多，收紧核心，保持胸口打开。"
                ActionErrorType.KNEE_VALGUS -> "膝盖有内扣趋势，主动让膝盖对准脚尖。"
                ActionErrorType.PUSH_UP_TOO_SHALLOW,
                ActionErrorType.WAIST_COLLAPSE,
                ActionErrorType.HIPS_TOO_HIGH,
                ActionErrorType.PUSH_UP_TOO_FAST -> "保持动作控制。"
            }
        }
    }

    private fun BodyFeatures.averageKneeAngle(): Double? {
        val left = leftKneeAngle ?: return null
        val right = rightKneeAngle ?: return null
        return ((left + right) / 2.0).roundToInt().toDouble()
    }
}
