package com.neurafit.app.ui

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.data.DashboardWelcomeRepository
import com.neurafit.app.data.DailyStepRecord
import com.neurafit.app.data.StepCountStore
import com.neurafit.app.data.StepSyncResult
import com.neurafit.app.data.StepSyncRepository
import com.neurafit.app.data.TrainingRecord
import com.neurafit.app.data.TrainingRepository
import com.neurafit.app.data.TrainingDurationRepository
import com.neurafit.app.data.TrainingDurationRepositoryResult
import java.time.LocalDate
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DEFAULT_DASHBOARD_DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

enum class DashboardMetric {
    STEPS,
    CALORIES,
    DURATION,
}

data class DashboardChartData(
    val valueLabel: String = "--",
    val unitLabel: String = "",
    val points: List<Float> = List(7) { 0f },
    val dayLabels: List<String> = DEFAULT_DASHBOARD_DAY_LABELS,
)

data class DashboardTimeUiState(
    val isLoading: Boolean = true,
    val totalStepsLabel: String = "--",
    val totalDurationLabel: String = "--",
    val longestSessionDurationLabel: String = "--",
    val totalCaloriesLabel: String = "--",
    val yesterdayWorkoutCount: Int = 0,
    val aiAdvice: String = "保持训练节奏",
    val selectedMetric: DashboardMetric = DashboardMetric.CALORIES,
    val chartData: DashboardChartData = DashboardChartData(),
    val subtitle: String = "正在同步训练历史...",
    val errorMessage: String? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TrainingDurationRepository(application)
    private val trainingRepository = TrainingRepository.create(application)
    private val welcomeRepository = DashboardWelcomeRepository(application)
    private val stepCountStore = StepCountStore(application)
    private val stepSyncRepository = StepSyncRepository(application)
    private val sensorManager = application.getSystemService(SensorManager::class.java)
    private val _timeUiState = MutableStateFlow(DashboardTimeUiState())
    val timeUiState: StateFlow<DashboardTimeUiState> = _timeUiState.asStateFlow()
    private var cachedWeeklyData: DashboardWeeklyData = DashboardWeeklyData.empty()
    private var isStepTracking = false
    private var stepCounterSensor: Sensor? = null
    private var lastSyncedStepSignature: String? = null
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val rawStepCount = event?.values?.firstOrNull()?.toInt() ?: return
            val todayStepCount = stepCountStore.saveSensorReading(rawStepCount)
            updateStepUi(todayStepCount, stepCountStore.getLastNDays(7))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    init {
        refresh()
    }

    fun selectMetric(metric: DashboardMetric) {
        val currentState = _timeUiState.value
        _timeUiState.value = _timeUiState.value.copy(
            selectedMetric = metric,
            chartData = cachedWeeklyData.toChartData(
                metric = metric,
                totalStepsLabel = currentState.totalStepsLabel,
                totalDurationLabel = currentState.totalDurationLabel,
                totalCaloriesLabel = currentState.totalCaloriesLabel,
            ),
        )
    }

    fun startStepTracking() {
        if (isStepTracking) return
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        stepCounterSensor = sensor
        sensorManager.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_UI)
        isStepTracking = true
        updateStepUi(
            todayStepCount = stepCountStore.getTodayStepCount(),
            stepRecords = stepCountStore.getLastNDays(7),
        )
    }

    fun stopStepTracking() {
        if (!isStepTracking) return
        sensorManager?.unregisterListener(stepListener, stepCounterSensor)
        isStepTracking = false
    }

    fun refresh() {
        viewModelScope.launch {
            val selectedMetric = _timeUiState.value.selectedMetric
            val stepRecords = stepCountStore.getLastNDays(7)
            val todayStepCount = stepCountStore.getTodayStepCount()
            _timeUiState.value = _timeUiState.value.copy(
                isLoading = true,
                errorMessage = null,
                subtitle = "正在同步训练历史...",
                totalStepsLabel = formatSteps(todayStepCount),
            )
            val records = trainingRepository.getTrainingRecords()
            val yesterdayWorkoutCount = countYesterdayWorkouts(records)
            val fallbackAdvice = buildFallbackAdvice(yesterdayWorkoutCount, records)
            syncStepRecords(stepRecords)
            when (val result = repository.syncAndFetchSummary()) {
                is TrainingDurationRepositoryResult.Success -> {
                    cachedWeeklyData = buildWeeklyData(records, stepRecords)
                    val aiAdvice = welcomeRepository.fetchAdvice(fallbackAdvice)
                    _timeUiState.value = DashboardTimeUiState(
                        isLoading = false,
                        totalStepsLabel = formatSteps(todayStepCount),
                        totalDurationLabel = formatDuration(result.summary.totalDurationMs),
                        longestSessionDurationLabel = formatDuration(result.summary.longestSessionDurationMs),
                        totalCaloriesLabel = formatCalories(result.summary.totalCaloriesKcal),
                        yesterdayWorkoutCount = yesterdayWorkoutCount,
                        aiAdvice = aiAdvice,
                        selectedMetric = selectedMetric,
                        chartData = cachedWeeklyData.toChartData(
                            metric = selectedMetric,
                            totalStepsLabel = formatSteps(todayStepCount),
                            totalDurationLabel = formatDuration(result.summary.totalDurationMs),
                            totalCaloriesLabel = formatCalories(result.summary.totalCaloriesKcal),
                        ),
                        subtitle = formatDelta(result.summary.deltaFromLastWeekMs),
                    )
                }
                is TrainingDurationRepositoryResult.Error -> {
                    _timeUiState.value = DashboardTimeUiState(
                        isLoading = false,
                        totalStepsLabel = formatSteps(todayStepCount),
                        totalDurationLabel = "--",
                        longestSessionDurationLabel = "--",
                        totalCaloriesLabel = "--",
                        yesterdayWorkoutCount = yesterdayWorkoutCount,
                        aiAdvice = fallbackAdvice,
                        selectedMetric = selectedMetric,
                        chartData = cachedWeeklyData.toChartData(
                            metric = selectedMetric,
                            totalStepsLabel = formatSteps(todayStepCount),
                            totalDurationLabel = "--",
                            totalCaloriesLabel = "--",
                        ),
                        subtitle = result.message,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = durationMs / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L -> "${hours}小时${minutes}分"
            minutes > 0L -> "${minutes}分"
            durationMs > 0L -> "<1分"
            else -> "12分"
        }
    }

    private fun formatDelta(deltaMs: Long): String {
        if (deltaMs == 0L) {
            return "与上周持平"
        }
        val prefix = if (deltaMs > 0L) "较上周增加 " else "较上周减少 "
        return "$prefix${formatDuration(abs(deltaMs))}"
    }

    private fun formatCalories(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format("%.1f", value)
        }

    private fun formatSteps(value: Int): String =
        value.coerceAtLeast(0).toString()

    private fun buildWeeklyData(
        records: List<TrainingRecord>,
        stepRecords: List<DailyStepRecord>,
    ): DashboardWeeklyData {
        val calendar = Calendar.getInstance()
        normalizeToDayStart(calendar)
        val todayStart = calendar.timeInMillis
        val durationByDay = MutableList(7) { 0L }
        val caloriesByDay = MutableList(7) { 0.0 }
        val stepsByDay = MutableList(7) { 0 }
        val dayLabels = MutableList(7) { index ->
            val labelCalendar = Calendar.getInstance().apply {
                timeInMillis = todayStart - (6 - index) * DAY_MS
            }
            dayLabelFor(labelCalendar)
        }
        val stepCountByDate = stepRecords.associate { it.date to it.stepCount }
        for (index in 0 until 7) {
            val date = LocalDate.now().minusDays((6 - index).toLong()).toString()
            stepsByDay[index] = stepCountByDate[date] ?: 0
        }

        records.forEach { record ->
            val dayStart = startOfDay(record.timestamp)
            val dayOffset = ((todayStart - dayStart) / DAY_MS).toInt()
            if (dayOffset !in 0..6) return@forEach

            val bucketIndex = 6 - dayOffset
            durationByDay[bucketIndex] += record.durationMs?.coerceAtLeast(0L) ?: 0L
            caloriesByDay[bucketIndex] += record.toCalories()
        }

        return DashboardWeeklyData(
            dayLabels = dayLabels,
            stepsByDay = stepsByDay,
            durationByDayMs = durationByDay,
            caloriesByDay = caloriesByDay,
        )
    }

    fun syncStoredStepRecords() {
        viewModelScope.launch {
            syncStepRecords(stepCountStore.getLastNDays(7))
        }
    }

    private fun syncStepRecords(stepRecords: List<DailyStepRecord>) {
        val signature = stepRecords.joinToString(separator = "|") { "${it.date}:${it.stepCount}" }
        if (signature == lastSyncedStepSignature) return
        viewModelScope.launch {
            when (stepSyncRepository.sync(stepRecords)) {
                StepSyncResult.Success -> lastSyncedStepSignature = signature
                is StepSyncResult.Error -> Unit
            }
        }
    }

    private fun updateStepUi(todayStepCount: Int, stepRecords: List<DailyStepRecord>) {
        val currentState = _timeUiState.value
        cachedWeeklyData = cachedWeeklyData.withSteps(stepRecords)
        _timeUiState.value = currentState.copy(
            totalStepsLabel = formatSteps(todayStepCount),
            chartData = cachedWeeklyData.toChartData(
                metric = currentState.selectedMetric,
                totalStepsLabel = formatSteps(todayStepCount),
                totalDurationLabel = currentState.totalDurationLabel,
                totalCaloriesLabel = currentState.totalCaloriesLabel,
            ),
        )
    }

    private fun countYesterdayWorkouts(records: List<TrainingRecord>): Int {
        val calendar = Calendar.getInstance()
        normalizeToDayStart(calendar)
        val todayStart = calendar.timeInMillis
        val yesterdayStart = todayStart - DAY_MS
        return records.count { record ->
            record.timestamp in yesterdayStart until todayStart
        }
    }

    private fun buildFallbackAdvice(
        yesterdayWorkoutCount: Int,
        records: List<TrainingRecord>,
    ): String {
        val latestAction = records.firstOrNull()?.actionName
        return sanitizeAdvice(
            when {
                yesterdayWorkoutCount >= 3 -> "注意恢复放松"
                yesterdayWorkoutCount >= 1 -> "今天延续节奏"
                latestAction == WorkoutActions.PUSH_UP -> "核心继续收紧"
                latestAction == WorkoutActions.SQUAT -> "蹲底保持稳定"
                else -> "今天先练一组"
            },
        )
    }

    private fun sanitizeAdvice(value: String): String =
        value.trim()
            .replace(Regex("[，。！？、；：,.!?;:]"), "")
            .let { text -> text.asSequence().take(12).joinToString(separator = "") }
            .ifBlank { "保持训练节奏" }

    private fun startOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        normalizeToDayStart(calendar)
        return calendar.timeInMillis
    }

    private fun normalizeToDayStart(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    private fun dayLabelFor(calendar: Calendar): String =
        when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> "周日"
        }

    private fun TrainingRecord.toCalories(): Double =
        when (actionName) {
            WorkoutActions.PUSH_UP -> count * PUSH_UP_KCAL_PER_REP
            else -> count * SQUAT_KCAL_PER_REP
        }

    private data class DashboardWeeklyData(
        val dayLabels: List<String>,
        val stepsByDay: List<Int>,
        val durationByDayMs: List<Long>,
        val caloriesByDay: List<Double>,
    ) {
        fun toChartData(
            metric: DashboardMetric,
            totalStepsLabel: String,
            totalDurationLabel: String,
            totalCaloriesLabel: String,
        ): DashboardChartData =
            when (metric) {
                DashboardMetric.STEPS ->
                    DashboardChartData(
                        valueLabel = totalStepsLabel,
                        unitLabel = "步",
                        points = stepsByDay.map { it.toFloat() },
                        dayLabels = dayLabels,
                    )

                DashboardMetric.CALORIES ->
                    DashboardChartData(
                        valueLabel = totalCaloriesLabel,
                        unitLabel = "千卡",
                        points = caloriesByDay.map { it.toFloat() },
                        dayLabels = dayLabels,
                    )

                DashboardMetric.DURATION ->
                    DashboardChartData(
                        valueLabel = totalDurationLabel,
                        unitLabel = "",
                        points = durationByDayMs.map { (it / 60_000f) },
                        dayLabels = dayLabels,
                    )
            }

        fun withSteps(stepRecords: List<DailyStepRecord>): DashboardWeeklyData {
            val stepCountByDate = stepRecords.associate { it.date to it.stepCount }
            val updatedSteps = dayLabels.indices.map { index ->
                val date = LocalDate.now().minusDays((dayLabels.lastIndex - index).toLong()).toString()
                stepCountByDate[date] ?: 0
            }
            return copy(stepsByDay = updatedSteps)
        }

        companion object {
            fun empty(): DashboardWeeklyData =
                DashboardWeeklyData(
                    dayLabels = DEFAULT_DASHBOARD_DAY_LABELS,
                    stepsByDay = List(7) { 0 },
                    durationByDayMs = List(7) { 0L },
                    caloriesByDay = List(7) { 0.0 },
                )
        }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val SQUAT_KCAL_PER_REP = 0.7
        private const val PUSH_UP_KCAL_PER_REP = 0.6
    }
}
