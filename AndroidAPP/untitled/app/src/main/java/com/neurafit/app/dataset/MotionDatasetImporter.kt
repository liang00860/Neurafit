package com.neurafit.app.dataset

import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.WorkoutActions

object MotionDatasetImporter {
    private const val NONE_VALUE = "NONE"

    fun parseCsv(csvText: String): List<MotionDatasetSample> {
        val rows = csvText
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map(::splitCsvLine)
            .toList()

        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map { it.trim() }
        return rows.drop(1).mapIndexedNotNull { index, row ->
            if (row.all { it.isBlank() }) {
                null
            } else {
                row.toSample(header = header, lineNumber = index + 2)
            }
        }
    }

    private fun List<String>.toSample(
        header: List<String>,
        lineNumber: Int,
    ): MotionDatasetSample {
        fun value(name: String): String? {
            val columnIndex = header.indexOf(name)
            return if (columnIndex in indices) get(columnIndex).trim().takeIf(String::isNotBlank) else null
        }

        return MotionDatasetSample(
            sampleId = value("sampleId") ?: "line-$lineNumber",
            actionName = value("actionName") ?: WorkoutActions.SQUAT,
            expectedCount = value("expectedCount")?.toIntOrNull(),
            expectedErrors = parseErrors(value("expectedErrors")),
            leftKneeAngle = value("leftKneeAngle")?.toDoubleOrNull(),
            rightKneeAngle = value("rightKneeAngle")?.toDoubleOrNull(),
            torsoTiltAngle = value("torsoTiltAngle")?.toDoubleOrNull(),
            leftRightKneeDistance = value("leftRightKneeDistance")?.toDoubleOrNull(),
            leftElbowAngle = value("leftElbowAngle")?.toDoubleOrNull(),
            rightElbowAngle = value("rightElbowAngle")?.toDoubleOrNull(),
            bodyLineAngle = value("bodyLineAngle")?.toDoubleOrNull(),
            bodyDescent = value("bodyDescent")?.toDoubleOrNull(),
            hipOffsetFromBodyLine = value("hipOffsetFromBodyLine")?.toDoubleOrNull(),
            repDurationMs = value("repDurationMs")?.toLongOrNull(),
        )
    }

    private fun parseErrors(rawValue: String?): Set<ActionErrorType> {
        if (rawValue.isNullOrBlank()) return setOf(ActionErrorType.NONE)

        val errors = rawValue
            .split('|', ';')
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .mapNotNull { value ->
                if (value.equals(NONE_VALUE, ignoreCase = true)) {
                    ActionErrorType.NONE
                } else {
                    runCatching { ActionErrorType.valueOf(value) }.getOrNull()
                }
            }
            .toSet()

        return if (errors.isEmpty()) setOf(ActionErrorType.NONE) else errors
    }

    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && insideQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> {
                    insideQuotes = !insideQuotes
                }
                char == ',' && !insideQuotes -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        cells += current.toString()
        return cells
    }
}
