package com.neurafit.app.learning

import com.neurafit.app.analysis.ActionErrorType

object TrainingAdviceGenerator {
    fun generate(profile: UserActionProfile): String {
        val errorAdvice = when (profile.mainError) {
            ActionErrorType.SQUAT_TOO_SHALLOW ->
                "下一次训练建议先做髋关节活动练习，再用慢速深蹲控制节奏，逐步提高下蹲幅度。"
            ActionErrorType.BACK_LEAN_TOO_MUCH ->
                "下一次训练建议加入核心收紧练习和靠墙深蹲，重点保持背部稳定，避免起身时身体过度前倾。"
            ActionErrorType.KNEE_VALGUS ->
                "下一次训练建议先做臀中肌激活，再进行弹力带深蹲，注意让膝盖始终和脚尖方向一致。"
            ActionErrorType.PUSH_UP_TOO_SHALLOW ->
                "下一次训练建议先做跪姿俯卧撑或慢速离心俯卧撑，逐步增加下降幅度，确保每次动作有足够深度。"
            ActionErrorType.WAIST_COLLAPSE ->
                "下一次训练建议加入平板支撑和核心收紧练习，俯卧撑时主动夹紧臀部，避免腰部下塌。"
            ActionErrorType.HIPS_TOO_HIGH ->
                "下一次训练建议先练习高位平板支撑，动作中降低髋部，让肩、髋、脚踝保持接近一条直线。"
            ActionErrorType.PUSH_UP_TOO_FAST ->
                "下一次训练建议采用慢速节奏，下降和撑起都保持稳定控制，先保证动作质量再提高次数。"
            ActionErrorType.NONE ->
                "当前动作质量比较稳定，建议保持现在的训练节奏，并在动作标准的前提下适当增加难度。"
        }

        val trendAdvice = when (profile.improvementTrend) {
            "进步明显" -> "最近表现进步明显，可以适当提高训练难度。"
            "状态下降" -> "最近状态有所下降，建议降低训练强度，优先保证每一次动作标准。"
            else -> "最近表现基本稳定，建议继续保持频率，并逐步提高动作质量。"
        }

        return "$errorAdvice\n$trendAdvice"
    }
}
