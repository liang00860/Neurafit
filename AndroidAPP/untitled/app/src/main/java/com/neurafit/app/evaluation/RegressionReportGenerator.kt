package com.neurafit.app.evaluation

import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.WorkoutActions
import java.util.Locale

object RegressionReportGenerator {
    fun generateMarkdown(result: ThresholdOptimizationResult): String {
        val builder = StringBuilder()
        builder.appendLine("# NeuraFit 规则库评估报告")
        builder.appendLine()
        builder.appendLine("- 动作类型：${WorkoutActions.displayName(result.actionName)}")
        builder.appendLine("- 样本数量：${result.optimized.metrics.sampleCount}")
        builder.appendLine("- 基线准确率：${result.baseline.metrics.exactMatchAccuracy.formatPercent()}")
        builder.appendLine("- 优化后准确率：${result.optimized.metrics.exactMatchAccuracy.formatPercent()}")
        builder.appendLine()
        builder.appendLine("## 推荐阈值")
        builder.appendLine()
        result.squatThreshold?.let { threshold ->
            builder.appendLine("- 深蹲最低膝角阈值：${threshold.maxKneeAngleAtBottom.formatNumber()}°")
            builder.appendLine("- 背部前倾阈值：${threshold.maxBackLeanAngle.formatNumber()}°")
            builder.appendLine("- 最小膝盖距离：${threshold.minKneeDistance.formatNumber()}")
        }
        result.pushUpThreshold?.let { threshold ->
            builder.appendLine("- 俯卧撑最低肘角阈值：${threshold.maxElbowAngleAtBottom.formatNumber()}°")
            builder.appendLine("- 身体直线程度阈值：${threshold.minBodyLineAngle.formatNumber()}°")
            builder.appendLine("- 最小下降幅度：${threshold.minDescent.formatNumber()}")
        }
        builder.appendLine()
        builder.appendLine("## 各错误类型指标")
        builder.appendLine()
        builder.appendLine("| 错误类型 | Precision | Recall | F1 |")
        builder.appendLine("|---|---:|---:|---:|")
        result.optimized.metrics.perError.forEach { (errorType, metric) ->
            builder.appendLine(
                "| ${errorType.displayLabel()} | ${metric.precision.formatPercent()} | ${metric.recall.formatPercent()} | ${metric.f1.formatPercent()} |",
            )
        }
        builder.appendLine()
        builder.appendLine("## 误判样本")
        builder.appendLine()
        val mismatches = result.optimized.predictions.filterNot(SamplePrediction::isExactMatch)
        if (mismatches.isEmpty()) {
            builder.appendLine("未发现误判样本。")
        } else {
            builder.appendLine("| 样本 ID | 人工标注 | 系统判断 |")
            builder.appendLine("|---|---|---|")
            mismatches.forEach { prediction ->
                builder.appendLine(
                    "| ${prediction.sampleId} | ${prediction.expectedErrors.displayLabels()} | ${prediction.predictedErrors.displayLabels()} |",
                )
            }
        }

        return builder.toString()
    }

    private fun Set<ActionErrorType>.displayLabels(): String =
        joinToString(separator = "、") { it.displayLabel() }

    private fun ActionErrorType.displayLabel(): String =
        when (this) {
            ActionErrorType.NONE -> "无明显错误"
            ActionErrorType.SQUAT_TOO_SHALLOW -> "下蹲不足"
            ActionErrorType.BACK_LEAN_TOO_MUCH -> "背部前倾"
            ActionErrorType.KNEE_VALGUS -> "膝盖内扣"
            ActionErrorType.PUSH_UP_TOO_SHALLOW -> "下降幅度不足"
            ActionErrorType.WAIST_COLLAPSE -> "塌腰"
            ActionErrorType.HIPS_TOO_HIGH -> "臀部过高"
            ActionErrorType.PUSH_UP_TOO_FAST -> "动作过快"
        }

    private fun Double.formatPercent(): String =
        String.format(Locale.US, "%.1f%%", coerceIn(0.0, 1.0) * 100)

    private fun Double.formatNumber(): String =
        String.format(Locale.US, "%.2f", this)
}
