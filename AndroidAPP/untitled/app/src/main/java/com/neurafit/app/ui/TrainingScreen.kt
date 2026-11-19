package com.neurafit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neurafit.app.data.AiTrainingPlan
import com.neurafit.app.data.AiTrainingPlanDay
import com.neurafit.app.data.AiTrainingPlanItem

private val TrainingBlack = Color(0xFF050505)
private val TrainingPanel = Color(0xFF111111)
private val TrainingStroke = Color(0xFF2A2A2A)
private val CardText = Color(0xFFFFFFFF)
private val LightGrayCardStart = Color(0xFF333333)
private val LightGrayCardEnd = Color(0xFF1A1A1A)
private val DarkGrayCardStart = Color(0xFF1A1A1A)
private val DarkGrayCardEnd = Color(0xFF0D0D0D)
private val HomeKcalCardColor = Color(0xFFC6EA7E)

@Composable
fun TrainingScreen(
    onStartWorkout: (String) -> Unit,
    trainingPlanViewModel: TrainingPlanViewModel = viewModel(),
) {
    val trainingPlanState by trainingPlanViewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrainingBlack),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 60.dp, bottom = 120.dp),
        ) {
            TrainingTopArea()
            Spacer(Modifier.height(28.dp))
            AiTrainingPlanFeaturedCard(
                uiState = trainingPlanState,
                onGeneratePlan = trainingPlanViewModel::requestPlan,
            )
            Spacer(Modifier.height(26.dp))
            TrainingCategoryTabs()
            Spacer(Modifier.height(18.dp))
            TrainingHomeCards.forEachIndexed { index, card ->
                TrainingWorkoutCard(
                    card = card,
                    onStartWorkout = onStartWorkout,
                )
                if (index != TrainingHomeCards.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        if (trainingPlanState.isDialogVisible) {
            AiTrainingPlanDialog(
                uiState = trainingPlanState,
                onAdoptPlan = trainingPlanViewModel::adoptPreviewPlan,
                onDismiss = trainingPlanViewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun TrainingStatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "7:22",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "5G",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
            )
            SignalBars()
            BatteryIndicator()
        }
    }
}

@Composable
private fun SignalBars() {
    Row(
        modifier = Modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(5.dp, 8.dp, 11.dp, 14.dp).forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.85f)),
            )
        }
    }
}

@Composable
private fun BatteryIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
        }
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.8f)),
        )
    }
}

@Composable
private fun TrainingTopArea() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "Training",
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
private fun AiTrainingPlanFeaturedCard(
    uiState: TrainingPlanUiState,
    onGeneratePlan: () -> Unit,
) {
    val adoptedPlan = uiState.adoptedPlan
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(HomeKcalCardColor, HomeKcalCardColor),
                ),
            )
            .clickable(enabled = !uiState.isLoadingRecommendation) { onGeneratePlan() },
    ) {
        RemoteImage(
            url = TrainingImageUrls.UpperBody,
            description = "AI training plan",
            alpha = 0.98f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(178.dp)
                .fillMaxHeight(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to Color.Transparent,
                        0.48f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.20f),
                    ),
                ),
        )
        ProgressBadge(
            text = if (uiState.isLoadingRecommendation) "..." else "AI",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DarkPill(text = if (adoptedPlan == null) "生成并同步" else "已同步Web")
            IconCircleButton(
                icon = FitIcon.Calendar,
                size = 28.dp,
                iconSize = 14.dp,
                background = Color.Black.copy(alpha = 0.24f),
                border = Color.White.copy(alpha = 0.16f),
                tint = Color.White,
                onClick = onGeneratePlan,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 18.dp)
                .width(190.dp),
        ) {
            Text(
                text = "肌肉构建",
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = adoptedPlan?.title ?: "AI训练计划",
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = if (adoptedPlan == null) {
                    uiState.message ?: "点击生成日历式训练安排"
                } else {
                    "已上传数据库，Web日历同步显示"
                },
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AiTrainingPlanDialog(
    uiState: TrainingPlanUiState,
    onAdoptPlan: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF101817))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                .padding(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI训练计划",
                            color = Color.White,
                            fontSize = 24.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.sp,
                        )
                        Text(
                            text = "确认后上传数据库，并同步到Web日历和Daily activity",
                            color = Color.White.copy(alpha = 0.64f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp,
                        )
                    }
                    AiPlanActionButton(
                        text = "关闭",
                        enabled = !uiState.isAdopting,
                        primary = false,
                        onClick = onDismiss,
                    )
                }

                when {
                    uiState.isLoadingRecommendation -> AiTrainingPlanLoading()
                    uiState.previewPlan != null -> AiTrainingPlanPreview(
                        plan = uiState.previewPlan,
                        isAdopting = uiState.isAdopting,
                        message = uiState.message,
                        onAdoptPlan = onAdoptPlan,
                    )
                    else -> Text(
                        text = uiState.message ?: "暂时没有生成计划，请稍后再试。",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiTrainingPlanLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(color = Mint, strokeWidth = 3.dp)
        Text(
            text = "AI大模型正在分析你的训练记录...",
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AiTrainingPlanPreview(
    plan: AiTrainingPlan,
    isAdopting: Boolean,
    message: String?,
    onAdoptPlan: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = plan.title,
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
            )
            Text(
                text = plan.summary,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            plan.days.forEachIndexed { index, day ->
                AiTrainingPlanDayChip(index = index, day = day)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            plan.days.forEachIndexed { index, day ->
                AiTrainingPlanDayCard(index = index, day = day)
            }
        }

        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            )
        }

        AiPlanActionButton(
            text = if (isAdopting) "上传中..." else "采用计划并同步Web",
            enabled = !isAdopting,
            primary = true,
            onClick = onAdoptPlan,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AiTrainingPlanDayChip(index: Int, day: AiTrainingPlanDay) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.11f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "D${index + 1}",
            color = Mint,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
        Text(
            text = day.date.takeLast(5),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Text(
            text = day.focus,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AiTrainingPlanDayCard(index: Int, day: AiTrainingPlanDay) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "第${index + 1}天 · ${day.title}",
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = "${day.date} / ${day.focus}",
                    color = Mint.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
            }
        }
        day.items.take(4).forEach { item ->
            Text(
                text = item.describe(),
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            )
        }
        if (day.note.isNotBlank()) {
            Text(
                text = day.note,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AiPlanActionButton(
    text: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (primary) Mint else Color.White.copy(alpha = 0.10f))
            .border(1.dp, if (primary) Mint else Color.White.copy(alpha = 0.14f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (primary) Color(0xFF061312) else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun AiTrainingPlanItem.describe(): String {
    val target = when {
        reps != null -> "${reps}次"
        durationMinutes != null -> "${durationMinutes}分钟"
        else -> "按状态完成"
    }
    return "$name ${sets}组 x $target · 休息${restSeconds}秒"
}

@Composable
private fun FeaturedWorkoutCard(
    uiState: TrainingPlanUiState,
    onGeneratePlan: () -> Unit,
) {
    val adoptedPlan = uiState.adoptedPlan
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(HomeKcalCardColor, HomeKcalCardColor),
                ),
            )
            .clickable(enabled = !uiState.isLoadingRecommendation) { onGeneratePlan() },
    ) {
        RemoteImage(
            url = TrainingImageUrls.UpperBody,
            description = "AI training plan",
            alpha = 0.98f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(178.dp)
                .fillMaxHeight(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to Color.Transparent,
                        0.48f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.20f),
                    ),
                ),
        )
        ProgressBadge(
            text = if (uiState.isLoadingRecommendation) "..." else "AI",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DarkPill(text = "专业训练")
            MiniAvatarStack(urls = TrainingAvatars, size = 21)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 18.dp)
                .width(190.dp),
        ) {
            Text(
                text = "肌肉构建",
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "上肢",
                color = Color.White,
                fontSize = 25.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun ProgressBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.13f))
            .border(1.6.dp, Color.White.copy(alpha = 0.82f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DarkPill(text: String) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun MiniAvatarStack(
    urls: List<String>,
    size: Int,
    modifier: Modifier = Modifier,
) {
    val step = (size * 0.58f).toInt()
    Box(
        modifier = modifier
            .width((size + step * (urls.size - 1)).dp)
            .height(size.dp),
    ) {
        urls.take(3).forEachIndexed { index, url ->
            Box(
                modifier = Modifier
                    .offset(x = (step * index).dp)
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.60f))
                    .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                    .padding(1.dp),
            ) {
                RemoteImage(
                    url = url,
                    description = "Member",
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape),
                )
            }
        }
    }
}

@Composable
private fun TrainingCategoryTabs() {
    val tabs = listOf("全部", "有氧", "增肌", "饮食")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = index == 0
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (active) Mint else Color.Transparent)
                    .border(1.dp, if (active) Mint else TrainingStroke, PillShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun TrainingWorkoutCard(
    card: TrainingHomeCard,
    onStartWorkout: (String) -> Unit,
) {
    val colors = when (card.accent) {
        TrainingCardAccent.Lime -> listOf(LightGrayCardStart, LightGrayCardEnd)
        TrainingCardAccent.Orange -> listOf(DarkGrayCardStart, DarkGrayCardEnd)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(colors))
            .border(1.dp, TrainingStroke, RoundedCornerShape(28.dp))
            .clickable { onStartWorkout(card.actionName) },
    ) {
        RemoteImage(
            url = card.imageUrl,
            description = card.title,
            alpha = 0.96f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(150.dp)
                .fillMaxHeight(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to Color.Transparent,
                        0.54f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.10f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .padding(start = 17.dp, top = 17.dp, bottom = 15.dp)
                .width(160.dp),
        ) {
            Text(
                text = card.label,
                color = CardText.copy(alpha = 0.78f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = card.title,
                color = CardText,
                fontSize = 25.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(Color.White.copy(alpha = 0.34f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = card.level,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp,
                    )
                }
                Text(
                    text = "•",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                MiniAvatarStack(urls = TrainingAvatars, size = 21)
            }
        }
        IconCircleButton(
            icon = FitIcon.ArrowUpRight,
            size = 38.dp,
            iconSize = 17.dp,
            background = Color.Black.copy(alpha = 0.18f),
            border = Color.White.copy(alpha = 0.18f),
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            onClick = { onStartWorkout(card.actionName) },
        )
    }
}
