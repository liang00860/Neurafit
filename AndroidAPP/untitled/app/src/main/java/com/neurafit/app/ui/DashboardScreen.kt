package com.neurafit.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

// Dark Theme Colors defined in UiKit.kt
private val LightText = Color.White
private val SecondaryText = Color(0xFFA1A1AA)
private val AccentLime = Color(0xFFC6EA7E)
private val AccentOrange = Color(0xFFFF4D4D)

@Composable
fun DashboardScreen(
    onStartWorkout: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val context = LocalContext.current
    val timeUiState by viewModel.timeUiState.collectAsState()
    val activityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startStepTracking()
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startStepTracking()
        } else {
            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopStepTracking()
            viewModel.syncStoredStepRecords()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 60.dp, bottom = 120.dp)
        ) {
            // 1. Top status and greeting area
            GreetingSection()

            Spacer(modifier = Modifier.height(32.dp))

            // 2. User progress summary card
            ProgressSummaryCard(state = timeUiState)

            Spacer(modifier = Modifier.height(40.dp))

            // 3. Performance section title
            Text(
                text = "表现概览",
                color = LightText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Performance stats cards
            PerformanceCardsRow(
                state = timeUiState,
                onMetricSelected = viewModel::selectMetric,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 5. Total average chart card
            TotalAverageChartCard(state = timeUiState)

            Spacer(modifier = Modifier.height(32.dp))

            // 6. Total Training Time card
            TimeCard(
                state = timeUiState,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
}

@Composable
private fun GreetingSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "Home",
            color = Color.White,
            fontSize = 68.sp,
            lineHeight = 74.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(3.dp),
            ) {
                RemoteImage(
                    url = ImageUrls.Avatar,
                    description = "个人头像",
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape),
                )
            }
            IconCircleButton(
                icon = FitIcon.Bell,
                size = 44.dp,
                iconSize = 18.dp,
                background = Color.White.copy(alpha = 0.12f),
                border = Color.White.copy(alpha = 0.08f),
                tint = Color.White.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ProgressSummaryCard(state: DashboardTimeUiState) {
    val progress = (state.yesterdayWorkoutCount.coerceIn(0, 4) / 4f) * 360f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "欢迎回来",
                    color = LightText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "昨天完成了 ${state.yesterdayWorkoutCount} 次运动",
                    color = SecondaryText,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = state.aiAdvice,
                    color = AccentLime,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.1f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = AccentLime,
                        startAngle = -90f,
                        sweepAngle = progress,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.yesterdayWorkoutCount.toString(),
                        color = LightText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "昨日",
                        color = SecondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformanceCardsRow(
    state: DashboardTimeUiState,
    onMetricSelected: (DashboardMetric) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val isStepsSelected = state.selectedMetric == DashboardMetric.STEPS
        Box(
            modifier = Modifier
                .weight(1f)
                .background(if (isStepsSelected) AccentLime else DarkCard, RoundedCornerShape(20.dp))
                .clickable { onMetricSelected(DashboardMetric.STEPS) }
                .padding(16.dp)
        ) {
            Column {
                LineIcon(
                    icon = FitIcon.Activity,
                    tint = if (isStepsSelected) Color.Black else SecondaryText,
                    size = 20.dp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "步数",
                    color = if (isStepsSelected) Color.Black.copy(alpha = 0.7f) else SecondaryText,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (state.isLoading) "..." else state.totalStepsLabel,
                    color = if (isStepsSelected) Color.Black else LightText,
                    fontSize = 20.sp,
                    fontWeight = if (isStepsSelected) FontWeight.ExtraBold else FontWeight.Bold,
                )
            }
        }

        val isCaloriesSelected = state.selectedMetric == DashboardMetric.CALORIES
    // Card 2
    Box(
        modifier = Modifier
            .weight(1f)
            .background(if (isCaloriesSelected) AccentLime else DarkCard, RoundedCornerShape(20.dp))
            .clickable { onMetricSelected(DashboardMetric.CALORIES) }
            .padding(16.dp)
    ) {
        Column {
            LineIcon(icon = FitIcon.Zap, tint = if (isCaloriesSelected) Color.Black else SecondaryText, size = 20.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "消耗千卡", color = if (isCaloriesSelected) Color.Black.copy(alpha = 0.7f) else SecondaryText, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (state.isLoading) "..." else state.totalCaloriesLabel,
                color = if (isCaloriesSelected) Color.Black else LightText,
                fontSize = 20.sp,
                fontWeight = if (isCaloriesSelected) FontWeight.ExtraBold else FontWeight.Bold,
            )
        }
    }

    val isDurationSelected = state.selectedMetric == DashboardMetric.DURATION
    // Card 3
    Box(
        modifier = Modifier
            .weight(1f)
            .background(if (isDurationSelected) AccentLime else DarkCard, RoundedCornerShape(20.dp))
            .clickable { onMetricSelected(DashboardMetric.DURATION) }
            .padding(16.dp)
    ) {
        Column {
            LineIcon(icon = FitIcon.Timer, tint = if (isDurationSelected) Color.Black else SecondaryText, size = 20.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "总时长", color = if (isDurationSelected) Color.Black.copy(alpha = 0.7f) else SecondaryText, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (state.isLoading) "..." else state.totalDurationLabel,
                color = if (isDurationSelected) Color.Black else LightText,
                fontSize = 20.sp,
                fontWeight = if (isDurationSelected) FontWeight.ExtraBold else FontWeight.Bold,
            )
        }
    }
    }
}

@Composable
private fun TotalAverageChartCard(state: DashboardTimeUiState) {
    val chartData = state.chartData
    val maxPoint = chartData.points.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val normalizedPoints = chartData.points.map { point -> (point / maxPoint).coerceIn(0f, 1f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "平均总量",
                        color = SecondaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (state.isLoading) "..." else chartData.valueLabel,
                            color = AccentOrange,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (chartData.unitLabel.isNotBlank()) {
                            Text(
                                text = chartData.unitLabel,
                                color = SecondaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = when (state.selectedMetric) {
                                DashboardMetric.STEPS -> "步数"
                                DashboardMetric.CALORIES -> "千卡"
                                DashboardMetric.DURATION -> "时长"
                            },
                            color = LightText,
                            fontSize = 12.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = "每周", color = LightText, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val stepY = height / 5

                    for (i in 0..5) {
                        val y = i * stepY
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val stepX = width / 6
                    val dataPoints = normalizedPoints

                    val path = Path().apply {
                        moveTo(0f, height * (1 - dataPoints[0]))

                        for (i in 0 until dataPoints.size - 1) {
                            val x1 = i * stepX
                            val y1 = height * (1 - dataPoints[i])
                            val x2 = (i + 1) * stepX
                            val y2 = height * (1 - dataPoints[i+1])

                            val cx = (x1 + x2) / 2
                            cubicTo(cx, y1, cx, y2, x2, y2)
                        }
                    }

                    // Area Fill
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AccentOrange.copy(alpha = 0.4f),
                                AccentOrange.copy(alpha = 0.0f)
                            )
                        )
                    )

                    // Line Stroke
                    drawPath(
                        path = path,
                        color = AccentOrange,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // X-axis labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                chartData.dayLabels.forEach { day ->
                    Text(
                        text = day,
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun List<Float>.maxOrNull(): Float? {
    if (isEmpty()) return null
    var max = first()
    for (index in 1 until size) {
        if (this[index] > max) {
            max = this[index]
        }
    }
    return max
}

@Composable
private fun TimeCard(
    state: DashboardTimeUiState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkCard, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                LineIcon(icon = FitIcon.Timer, tint = AccentOrange, size = 34.dp)
                Spacer(Modifier.height(22.dp))
                MonoText(text = "最长一次训练时间", color = SecondaryText, size = 11, weight = FontWeight.Normal)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (state.isLoading) "..." else state.longestSessionDurationLabel,
                    color = LightText,
                    fontSize = 44.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MonoText(
                text = state.subtitle,
                color = SecondaryText.copy(alpha = 0.7f),
                size = 11,
                weight = FontWeight.Normal,
            )
        }
    }
}
