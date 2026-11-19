package com.neurafit.app.analysis

object PushUpAnalyzer {
    fun analyze(
        features: PushUpFeatures?,
        pushUpState: PushUpCounterSnapshot? = null,
        threshold: PushUpThreshold = PushUpThreshold(),
    ): CorrectionResult {
        if (features == null) {
            return CorrectionResult(
                score = 0,
                errors = setOf(ActionErrorType.NONE),
                message = "未检测到完整姿态，请调整站位，让肩、髋、脚踝进入画面。",
            )
        }

        val errors = linkedSetOf<ActionErrorType>()
        val elbowAngleAtBottom = pushUpState?.currentRepMinimumElbowAngle
            ?: pushUpState?.lastRepMinimumElbowAngle
            ?: features.averageElbowAngle()
        val shouldEvaluateDepth = pushUpState == null ||
            pushUpState.stage != PushUpStage.ARMS_STRAIGHT ||
            pushUpState.lastRepMinimumElbowAngle != null

        if (
            shouldEvaluateDepth &&
            elbowAngleAtBottom != null &&
            elbowAngleAtBottom > threshold.maxElbowAngleAtBottom
        ) {
            errors += ActionErrorType.PUSH_UP_TOO_SHALLOW
        }

        val maxDescent = pushUpState?.maxBodyDescentInCurrentRep
            ?: pushUpState?.lastRepMaxBodyDescent
            ?: features.bodyDescent
        if (
            shouldEvaluateDepth &&
            maxDescent != null &&
            maxDescent < threshold.minDescent
        ) {
            errors += ActionErrorType.PUSH_UP_TOO_SHALLOW
        }

        val hipOffset = features.hipOffsetFromBodyLine
        when {
            hipOffset != null && hipOffset > threshold.maxWaistSag -> {
                errors += ActionErrorType.WAIST_COLLAPSE
            }
            hipOffset != null && hipOffset < -threshold.maxHipPike -> {
                errors += ActionErrorType.HIPS_TOO_HIGH
            }
            features.bodyLineAngle != null && features.bodyLineAngle < threshold.minBodyLineAngle -> {
                errors += ActionErrorType.WAIST_COLLAPSE
            }
        }

        if (pushUpState?.lastRepTooFast == true) {
            errors += ActionErrorType.PUSH_UP_TOO_FAST
        }

        val visibleErrors = if (errors.isEmpty()) {
            setOf(ActionErrorType.NONE)
        } else {
            errors
        }

        return CorrectionResult(
            score = calculateScore(visibleErrors),
            errors = visibleErrors,
            message = buildMessage(visibleErrors),
        )
    }

    private fun calculateScore(errors: Set<ActionErrorType>): Int {
        val deduction = errors.sumOf { error ->
            when (error) {
                ActionErrorType.NONE -> 0
                ActionErrorType.PUSH_UP_TOO_SHALLOW -> 25
                ActionErrorType.WAIST_COLLAPSE -> 25
                ActionErrorType.HIPS_TOO_HIGH -> 20
                ActionErrorType.PUSH_UP_TOO_FAST -> 15
                ActionErrorType.SQUAT_TOO_SHALLOW,
                ActionErrorType.BACK_LEAN_TOO_MUCH,
                ActionErrorType.KNEE_VALGUS -> 0
            }
        }
        return (100 - deduction).coerceIn(0, 100)
    }

    private fun buildMessage(errors: Set<ActionErrorType>): String {
        if (ActionErrorType.NONE in errors) {
            return "俯卧撑节奏不错，继续保持身体成一直线。"
        }

        return errors.joinToString(separator = "\n") { error ->
            when (error) {
                ActionErrorType.PUSH_UP_TOO_SHALLOW -> "下降幅度不足，继续降低身体，让肘部弯曲更充分。"
                ActionErrorType.WAIST_COLLAPSE -> "有塌腰趋势，收紧核心和臀部，保持躯干稳定。"
                ActionErrorType.HIPS_TOO_HIGH -> "臀部过高，降低髋部，让肩、髋、脚踝接近一条直线。"
                ActionErrorType.PUSH_UP_TOO_FAST -> "动作过快，放慢节奏，下降和撑起都要稳定控制。"
                ActionErrorType.NONE -> "俯卧撑动作不错，继续保持。"
                ActionErrorType.SQUAT_TOO_SHALLOW,
                ActionErrorType.BACK_LEAN_TOO_MUCH,
                ActionErrorType.KNEE_VALGUS -> "保持动作控制。"
            }
        }
    }
}
