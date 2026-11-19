package com.neurafit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val MovementAccuracyPoints = listOf(0.92f, 0.84f, 0.88f, 0.78f, 0.86f, 0.90f)

@Composable
fun InsightsScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 60.dp, bottom = 120.dp)
                .widthIn(max = 1280.dp),
            verticalArrangement = Arrangement.spacedBy(42.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "History",
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
                            description = "Profile",
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        LineIcon(icon = FitIcon.Bell, tint = Color.White.copy(alpha = 0.72f), size = 18.dp)
                    }
                }
            }
            TrainingHistoryScreen()
            InsightCards()
            ProgressChart()
            RecoveryStatus()
        }
    }
}

@Composable
private fun InsightCards() {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 760.dp
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AccuracyCard(Modifier.weight(1f).aspectRatio(1f))
                CaloriesCard(Modifier.weight(1f).aspectRatio(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                AccuracyCard(Modifier.fillMaxWidth().height(220.dp))
                CaloriesCard(Modifier.fillMaxWidth().height(220.dp))
            }
        }
    }
}

@Composable
private fun AccuracyCard(modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                LineIcon(icon = FitIcon.Radar, tint = Purple, size = 34.dp)
                Text(
                    text = "86%",
                    color = Zinc950,
                    fontSize = 58.sp,
                    lineHeight = 58.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                )
            }
            MovementAccuracyVisual(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoText(text = "Movement Accuracy", color = Zinc500, size = 11, weight = FontWeight.Normal)
                PillTag(
                    text = "+4 pts",
                    background = Purple.copy(alpha = 0.10f),
                    color = Purple,
                    border = Purple.copy(alpha = 0.18f),
                )
            }
        }
    }
}

@Composable
private fun MovementAccuracyVisual(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val chartSize = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = chartSize * 0.43f
        val stroke = 1.dp.toPx()

        repeat(3) { index ->
            drawCircle(
                color = Zinc100,
                radius = radius * (index + 1) / 3f,
                center = center,
                style = Stroke(width = stroke),
            )
        }

        repeat(MovementAccuracyPoints.size) { index ->
            val angle = -PI / 2.0 + (2.0 * PI * index / MovementAccuracyPoints.size)
            val edge = Offset(
                x = center.x + cos(angle).toFloat() * radius,
                y = center.y + sin(angle).toFloat() * radius,
            )
            drawLine(
                color = Zinc100,
                start = center,
                end = edge,
                strokeWidth = stroke,
            )
        }

        val points = MovementAccuracyPoints.mapIndexed { index, value ->
            val angle = -PI / 2.0 + (2.0 * PI * index / MovementAccuracyPoints.size)
            Offset(
                x = center.x + cos(angle).toFloat() * radius * value,
                y = center.y + sin(angle).toFloat() * radius * value,
            )
        }
        val path = Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
            close()
        }

        drawPath(path = path, color = Purple.copy(alpha = 0.16f), style = Fill)
        drawPath(
            path = path,
            color = Purple,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        points.forEach { point ->
            drawCircle(color = Color.White, radius = 4.6.dp.toPx(), center = point)
            drawCircle(color = Purple, radius = 3.dp.toPx(), center = point)
        }
        drawCircle(color = Zinc950, radius = 4.dp.toPx(), center = center)
    }
}

@Composable
private fun CaloriesCard(modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, background = Zinc950, border = Zinc950) {
        RemoteImage(
            url = ImageUrls.Abstract,
            description = "Abstract",
            modifier = Modifier.matchParentSize(),
            alpha = 0.20f,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            LineIcon(icon = FitIcon.Flame, tint = Lime, size = 34.dp)
            Column {
                MonoText(text = "Calorie Balance", color = Zinc400, size = 11, weight = FontWeight.Normal)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Net -450",
                    color = Color.White,
                    fontSize = 44.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProgressChart() {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MonoText(text = "Weekly Progress", color = Zinc500, size = 11, weight = FontWeight.Normal)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Volume Load",
                        color = Zinc950,
                        fontSize = 36.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RangeChip("1W", selected = false)
                    RangeChip("1M", selected = true)
                    RangeChip("3M", selected = false)
                }
            }

            BarChart()
        }
    }
}

@Composable
private fun RangeChip(text: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) Zinc950 else Color.Transparent)
            .border(1.dp, if (selected) Zinc950 else Zinc200, PillShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Zinc950,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun BarChart() {
    val bars = listOf(
        "Mon" to 0.30f,
        "Tue" to 0.45f,
        "Wed" to 0.20f,
        "Thu" to 0.80f,
        "Fri" to 0.60f,
        "Sat" to 0.90f,
        "Sun" to 0.40f,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(288.dp),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val step = size.height / 4f
            repeat(4) { index ->
                val y = step * index
                drawLine(
                    color = Zinc100,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { (day, fraction) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (day == "Thu") {
                        Box(
                            modifier = Modifier
                                .clip(SmallCardShape)
                                .background(Zinc950)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            MonoText(text = day, color = Color.White, size = 10, weight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Spacer(Modifier.height(35.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(if (day == "Thu") Zinc950 else Zinc200),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoveryStatus() {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            text = "RECOVERY STATUS",
            color = Zinc950,
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 760.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SleepCard(Modifier.weight(1f))
                    ReadinessCard(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SleepCard(Modifier.fillMaxWidth())
                    ReadinessCard(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SleepCard(modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.height(136.dp)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                MonoText(text = "Sleep Score", color = Zinc500, size = 11, weight = FontWeight.Normal)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "92",
                    color = Zinc950,
                    fontSize = 48.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                )
            }
            Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val stroke = 6.dp.toPx()
                    drawCircle(
                        color = Zinc100,
                        radius = (size.minDimension - stroke) / 2f,
                        style = Stroke(width = stroke),
                    )
                    drawArc(
                        color = Lime,
                        startAngle = -90f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                LineIcon(icon = FitIcon.Moon, tint = Zinc950, size = 34.dp)
            }
        }
    }
}

@Composable
private fun ReadinessCard(modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.height(136.dp)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MonoText(text = "Readiness", color = Zinc500, size = 11, weight = FontWeight.Normal)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Optimal",
                    color = Zinc950,
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Lime.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(icon = FitIcon.Zap, tint = Lime, size = 34.dp)
            }
        }
    }
}
