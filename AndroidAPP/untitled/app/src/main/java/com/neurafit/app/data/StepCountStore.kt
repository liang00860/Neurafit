package com.neurafit.app.data

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DailyStepRecord(
    val date: String,
    val stepCount: Int,
    val source: String = "device_sensor",
)

class StepCountStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("step_count_store", Context.MODE_PRIVATE)
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun saveSensorReading(rawStepCount: Int, now: LocalDate = LocalDate.now()): Int {
        val todayKey = now.format(formatter)
        val baselineDate = preferences.getString(KEY_BASELINE_DATE, null)
        val baselineValue = preferences.getInt(KEY_BASELINE_VALUE, rawStepCount)
        val effectiveBaseline =
            if (baselineDate != todayKey || rawStepCount < baselineValue) {
                rawStepCount
            } else {
                baselineValue
            }
        val todayCount = (rawStepCount - effectiveBaseline).coerceAtLeast(0)

        preferences.edit()
            .putString(KEY_BASELINE_DATE, todayKey)
            .putInt(KEY_BASELINE_VALUE, effectiveBaseline)
            .putInt(dailyCountKey(todayKey), todayCount)
            .apply()
        pruneOlderThan(now.minusDays(MAX_HISTORY_DAYS.toLong()))
        return todayCount
    }

    fun getTodayStepCount(now: LocalDate = LocalDate.now()): Int =
        preferences.getInt(dailyCountKey(now.format(formatter)), 0)

    fun getLastNDays(dayCount: Int, now: LocalDate = LocalDate.now()): List<DailyStepRecord> =
        (dayCount - 1 downTo 0).map { offset ->
            val date = now.minusDays(offset.toLong()).format(formatter)
            DailyStepRecord(
                date = date,
                stepCount = preferences.getInt(dailyCountKey(date), 0),
            )
        }

    private fun pruneOlderThan(threshold: LocalDate) {
        val allEntries = preferences.all.keys
            .filter { it.startsWith(DAILY_COUNT_PREFIX) }
            .mapNotNull { key ->
                runCatching { LocalDate.parse(key.removePrefix(DAILY_COUNT_PREFIX), formatter) }
                    .getOrNull()
                    ?.let { date -> key to date }
            }
            .filter { (_, date) -> date.isBefore(threshold) }

        if (allEntries.isEmpty()) return
        val editor = preferences.edit()
        allEntries.forEach { (key, _) -> editor.remove(key) }
        editor.apply()
    }

    private fun dailyCountKey(date: String): String = DAILY_COUNT_PREFIX + date

    private companion object {
        private const val KEY_BASELINE_DATE = "baseline_date"
        private const val KEY_BASELINE_VALUE = "baseline_value"
        private const val DAILY_COUNT_PREFIX = "daily_count_"
        private const val MAX_HISTORY_DAYS = 21
    }
}
