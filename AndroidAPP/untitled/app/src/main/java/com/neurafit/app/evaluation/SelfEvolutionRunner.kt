package com.neurafit.app.evaluation

import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.dataset.MotionDatasetImporter

data class SelfEvolutionReport(
    val squatOptimization: ThresholdOptimizationResult?,
    val pushUpOptimization: ThresholdOptimizationResult?,
    val markdown: String,
)

object SelfEvolutionRunner {
    fun runFromCsv(csvText: String): SelfEvolutionReport {
        val samples = MotionDatasetImporter.parseCsv(csvText)
        val squatResult = if (samples.any { it.actionName == WorkoutActions.SQUAT }) {
            ThresholdSearchOptimizer.optimize(samples, WorkoutActions.SQUAT)
        } else {
            null
        }
        val pushUpResult = if (samples.any { it.actionName == WorkoutActions.PUSH_UP }) {
            ThresholdSearchOptimizer.optimize(samples, WorkoutActions.PUSH_UP)
        } else {
            null
        }

        val markdown = buildString {
            appendLine("# NeuraFit 自学习评估汇总")
            appendLine()
            appendLine("本报告基于人工标注测试集自动生成，用于评估当前规则库表现并推荐阈值调整。")
            appendLine()
            squatResult?.let {
                appendLine(RegressionReportGenerator.generateMarkdown(it))
                appendLine()
            }
            pushUpResult?.let {
                appendLine(RegressionReportGenerator.generateMarkdown(it))
                appendLine()
            }
        }

        return SelfEvolutionReport(
            squatOptimization = squatResult,
            pushUpOptimization = pushUpResult,
            markdown = markdown,
        )
    }
}
