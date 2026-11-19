package com.neurafit.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.data.TrainingRecord
import com.neurafit.app.learning.TrainingAdviceGenerator
import com.neurafit.app.learning.UserActionProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TrainingHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: TrainingHistoryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var isHistoryExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MonoText(text = "Local Records", color = Zinc500, size = 11, weight = FontWeight.Normal)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "TRAINING HISTORY",
                    color = Zinc950,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(Zinc950)
                    .clickable { viewModel.refresh() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                MonoText(text = "Refresh", color = Color.White, size = 10, weight = FontWeight.ExtraBold)
            }
        }

        when {
            uiState.isLoading -> HistoryNotice("Loading training records...")
            uiState.errorMessage != null -> HistoryNotice(uiState.errorMessage ?: "Unable to load training history.")
            uiState.records.isEmpty() -> HistoryNotice("No saved training records yet. Complete a workout to create one.")
            else -> {
                uiState.actionProfile?.let { profile ->
                    UserActionProfileCard(profile = profile)
                }
                
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    background = Color.White,
                    border = Zinc200
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isHistoryExpanded = !isHistoryExpanded }
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MonoText(
                                text = "Training Records",
                                color = Zinc950,
                                size = 14,
                                weight = FontWeight.ExtraBold,
                            )
                            LineIcon(
                                icon = FitIcon.ArrowRight,
                                tint = Zinc500,
                                modifier = Modifier.rotate(if (isHistoryExpanded) -90f else 90f)
                            )
                        }
                        
                        AnimatedVisibility(
                            visible = isHistoryExpanded,
                            enter = expandVertically(animationSpec = tween(300)),
                            exit = shrinkVertically(animationSpec = tween(300))
                        ) {
                            Box(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                                HistoryContent(
                                    records = uiState.records,
                                    selectedRecord = uiState.selectedRecord,
                                    onSelectRecord = viewModel::selectRecord,
                                    onCloseDetails = viewModel::closeDetails,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserActionProfileCard(profile: UserActionProfile) {
    val trainingAdvice = remember(profile) { TrainingAdviceGenerator.generate(profile) }

    AppCard(modifier = Modifier.fillMaxWidth(), background = Color.White, border = Zinc200) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MonoText(text = "Action Profile", color = Zinc500, size = 11, weight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = WorkoutActions.displayName(profile.actionName),
                        color = Zinc950,
                        fontSize = 30.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = profile.averageScore.roundToInt().toString(),
                        color = Purple,
                        fontSize = 46.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp,
                    )
                    MonoText(text = "Avg Score", color = Zinc500, size = 10, weight = FontWeight.Normal)
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 620.dp
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileMetric("主要错误", profile.mainError.displayLabel(), Modifier.weight(1f))
                        ProfileMetric("最近趋势", profile.improvementTrend, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileMetric("主要错误", profile.mainError.displayLabel(), Modifier.fillMaxWidth())
                        ProfileMetric("最近趋势", profile.improvementTrend, Modifier.fillMaxWidth())
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                profile.displayErrorRates().forEach { (label, rate) ->
                    ErrorRateRow(label, rate)
                }
            }

            TrainingAdviceBox(advice = trainingAdvice)
        }
    }
}

@Composable
private fun TrainingAdviceBox(advice: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallCardShape)
            .background(Zinc100)
            .border(1.dp, Zinc200, SmallCardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MonoText(text = "下次训练建议", color = Zinc500, size = 10, weight = FontWeight.ExtraBold)
        Text(
            text = advice,
            color = Zinc950,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun ProfileMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(SmallCardShape)
            .background(Zinc100)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MonoText(text = label, color = Zinc500, size = 10, weight = FontWeight.Normal)
        Text(
            text = value,
            color = Zinc950,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ErrorRateRow(label: String, rate: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Zinc500,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
        Text(
            text = rate.formatPercent(),
            color = Zinc950,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun HistoryContent(
    records: List<TrainingRecord>,
    selectedRecord: TrainingRecord?,
    onSelectRecord: (TrainingRecord) -> Unit,
    onCloseDetails: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 820.dp
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                HistoryList(
                    records = records,
                    selectedRecordId = selectedRecord?.id,
                    onSelectRecord = onSelectRecord,
                    modifier = Modifier.weight(1.2f),
                )
                HistoryDetails(
                    record = selectedRecord ?: records.first(),
                    onClose = onCloseDetails,
                    modifier = Modifier.weight(0.8f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                HistoryList(
                    records = records,
                    selectedRecordId = selectedRecord?.id,
                    onSelectRecord = onSelectRecord,
                    modifier = Modifier.fillMaxWidth(),
                )
                selectedRecord?.let { record ->
                    HistoryDetails(
                        record = record,
                        onClose = onCloseDetails,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryList(
    records: List<TrainingRecord>,
    selectedRecordId: Long?,
    onSelectRecord: (TrainingRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        records.forEach { record ->
            HistoryRecordRow(
                record = record,
                selected = record.id == selectedRecordId,
                onClick = { onSelectRecord(record) },
            )
        }
    }
}

@Composable
private fun HistoryRecordRow(
    record: TrainingRecord,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        background = if (selected) Zinc950 else Color.White,
        border = if (selected) Zinc950 else Zinc200,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MonoText(
                        text = record.formatTime(),
                        color = if (selected) Zinc300 else Zinc500,
                        size = 10,
                        weight = FontWeight.Normal,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = WorkoutActions.displayName(record.actionName),
                        color = if (selected) Color.White else Zinc950,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${record.score}",
                    color = if (selected) Lime else Purple,
                    fontSize = 36.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HistoryChip("${record.count} reps", selected)
                HistoryChip(record.majorErrorLabel(), selected)
            }
        }
    }
}

@Composable
private fun HistoryDetails(
    record: TrainingRecord,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier, background = Zinc950, border = Zinc950) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoText(text = "Details", color = Lime, size = 11, weight = FontWeight.ExtraBold)
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .border(1.dp, Color.White.copy(alpha = 0.24f), PillShape)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    MonoText(text = "Close", color = Color.White, size = 9, weight = FontWeight.ExtraBold)
                }
            }
            Text(
                text = WorkoutActions.displayName(record.actionName),
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
            )
            if (record.actionName == WorkoutActions.PUSH_UP) {
                DetailRow("下降幅度不足", record.pushUpTooShallowCount.toString())
                DetailRow("塌腰", record.waistCollapseCount.toString())
                DetailRow("臀部过高", record.hipsTooHighCount.toString())
                DetailRow("动作过快", record.pushUpTooFastCount.toString())
                DetailRow("平均肘关节角度", record.avgElbowAngle.formatAngle())
                DetailRow("平均身体直线程度", record.avgBodyLineAngle.formatAngle())
                DetailRow("平均下降幅度", record.avgBodyDescent.formatDistance())
            } else {
                DetailRow("下蹲不足", record.squatTooShallowCount.toString())
                DetailRow("背部前倾", record.backLeanTooMuchCount.toString())
                DetailRow("膝盖内扣", record.kneeValgusCount.toString())
                DetailRow("平均膝盖角度", record.avgKneeAngle.formatAngle())
                DetailRow("平均背部倾斜角", record.avgBackLeanAngle.formatAngle())
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Zinc300,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun HistoryChip(text: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Zinc100)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        MonoText(
            text = text,
            color = if (selected) Color.White else Zinc500,
            size = 9,
            weight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun HistoryNotice(message: String) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(22.dp),
            color = Zinc500,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
    }
}

private fun TrainingRecord.formatTime(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun TrainingRecord.majorErrorLabel(): String {
    val errors = if (actionName == WorkoutActions.PUSH_UP) {
        listOf(
            "下降幅度不足" to pushUpTooShallowCount,
            "塌腰" to waistCollapseCount,
            "臀部过高" to hipsTooHighCount,
            "动作过快" to pushUpTooFastCount,
        )
    } else {
        listOf(
            "下蹲不足" to squatTooShallowCount,
            "背部前倾" to backLeanTooMuchCount,
            "膝盖内扣" to kneeValgusCount,
        )
    }
    val major = errors.maxByOrNull { it.second }
    return if (major == null || major.second == 0) "主要错误: 无" else "主要错误: ${major.first}"
}

private fun Double?.formatAngle(): String =
    this?.let { "${it.toInt()}°" } ?: "--"

private fun Double?.formatDistance(): String =
    this?.let { String.format("%.2f", it) } ?: "--"

private fun Double.formatPercent(): String =
    "${(this.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

private fun UserActionProfile.displayErrorRates(): List<Pair<String, Double>> {
    val rates = if (errorRates.isNotEmpty()) {
        errorRates
    } else {
        mapOf(
            ActionErrorType.SQUAT_TOO_SHALLOW to squatTooShallowRate,
            ActionErrorType.BACK_LEAN_TOO_MUCH to backLeanRate,
            ActionErrorType.KNEE_VALGUS to kneeValgusRate,
        )
    }
    return rates
        .filterKeys { it != ActionErrorType.NONE }
        .map { (type, rate) -> "${type.displayLabel()}出现率" to rate }
}

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
