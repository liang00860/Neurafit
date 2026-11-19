package com.neurafit.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.neurafit.app.R
import com.neurafit.app.analysis.WorkoutActions

enum class AuthState {
    Onboarding,
    Login,
    LoggedIn
}

@Composable
fun NeurafitApp() {
    NeurafitTheme {
        LoginScreenState.initialMode = AuthMode.SignIn
        val initialAuthState = AuthState.Onboarding.name
        var authState by rememberSaveable { mutableStateOf(initialAuthState) }
        val currentAuthState = AuthState.valueOf(authState)

        AnimatedContent(
            targetState = currentAuthState,
            transitionSpec = {
                val direction = if (targetState == AuthState.Login && initialState == AuthState.Onboarding) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else if (targetState == AuthState.Onboarding && initialState == AuthState.Login) {
                    AnimatedContentTransitionScope.SlideDirection.Right
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Left
                }
                (slideIntoContainer(direction, tween(500)) + fadeIn(tween(500))).togetherWith(
                    slideOutOfContainer(direction, tween(500)) + fadeOut(tween(500))
                )
            },
            label = "authTransition"
        ) { state ->
            when (state) {
                AuthState.Onboarding -> {
                    OnboardingScreen(
                        onLoginClick = { 
                            LoginScreenState.initialMode = AuthMode.SignIn
                            authState = AuthState.Login.name 
                        },
                        onJoinClick = { 
                            LoginScreenState.initialMode = AuthMode.Register
                            authState = AuthState.Login.name 
                        }
                    )
                }
                AuthState.Login -> {
                    LoginScreen(
                        initialMode = LoginScreenState.initialMode,
                        onLoginSuccess = { authState = AuthState.LoggedIn.name },
                        onBack = { authState = AuthState.Onboarding.name }
                    )
                }
                AuthState.LoggedIn -> {
                    MainApp(
                        onShowLogin = { authState = AuthState.Onboarding.name },
                    )
                }
            }
        }
    }
}

object LoginScreenState {
    var initialMode: AuthMode = AuthMode.SignIn
}

@Composable
fun NeurafitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Zinc50,
            surface = Color.White,
            primary = Zinc950,
            secondary = Purple,
            tertiary = Lime,
            onBackground = Zinc950,
            onSurface = Zinc950,
        ),
        content = content,
    )
}

@Composable
private fun MainApp(onShowLogin: () -> Unit) {
    var activeTabName by rememberSaveable { mutableStateOf(AppTab.Dashboard.name) }
    var pendingWorkoutActionName by rememberSaveable { mutableStateOf<String?>(null) }
    var activeWorkoutActionName by rememberSaveable { mutableStateOf(WorkoutActions.SQUAT) }
    var activeWorkoutModeName by rememberSaveable { mutableStateOf<String?>(null) }
    var isAiCardOpen by rememberSaveable { mutableStateOf(false) }
    val activeTab = AppTab.valueOf(activeTabName)

    Surface(modifier = Modifier.fillMaxSize(), color = Zinc50) {
        Crossfade(
            targetState = activeWorkoutModeName,
            animationSpec = tween(180),
            label = "workout-root",
        ) { modeName ->
            when (modeName?.let(WorkoutLaunchMode::valueOf)) {
                WorkoutLaunchMode.Local -> {
                    ActiveWorkoutScreen(
                        actionName = activeWorkoutActionName,
                        onClose = { activeWorkoutModeName = null },
                    )
                }
                WorkoutLaunchMode.MotionCoachStream -> {
                    MotionCoachStreamingScreen(
                        actionName = activeWorkoutActionName,
                        onClose = { activeWorkoutModeName = null },
                    )
                }
                null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainScaffold(
                            activeTab = activeTab,
                            onTabChange = { activeTabName = it.name },
                            onStartWorkout = { actionName ->
                                pendingWorkoutActionName = actionName
                            },
                            isAiCardOpen = isAiCardOpen,
                            onToggleAi = { isAiCardOpen = !isAiCardOpen },
                            onShowLogin = onShowLogin,
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = pendingWorkoutActionName != null,
                            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
                            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
                        ) {
                            val actionName = pendingWorkoutActionName ?: ""
                            WorkoutLaunchModePicker(
                                actionName = actionName,
                                onTrainOnPhone = {
                                    activeWorkoutActionName = actionName
                                    activeWorkoutModeName = WorkoutLaunchMode.Local.name
                                    pendingWorkoutActionName = null
                                },
                                onStreamToMotionCoach = {
                                    activeWorkoutActionName = actionName
                                    activeWorkoutModeName = WorkoutLaunchMode.MotionCoachStream.name
                                    pendingWorkoutActionName = null
                                },
                                onDismiss = {
                                    pendingWorkoutActionName = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(
    activeTab: AppTab,
    onTabChange: (AppTab) -> Unit,
    onStartWorkout: (String) -> Unit,
    isAiCardOpen: Boolean,
    onToggleAi: () -> Unit,
    onShowLogin: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                // Determine direction based on visual UI order in the Bottom Navigation Bar:
                // Dashboard (0) -> Insights (1) -> Training (2) -> Nutrition (3)
                val uiOrder = listOf(AppTab.Dashboard, AppTab.Insights, AppTab.Training, AppTab.Nutrition)
                val initialIndex = uiOrder.indexOf(initialState)
                val targetIndex = uiOrder.indexOf(targetState)
                
                val direction = if (targetIndex > initialIndex) {
                    // Clicking a tab to the right -> new screen slides in from the right
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    // Clicking a tab to the left -> new screen slides in from the left
                    AnimatedContentTransitionScope.SlideDirection.Right
                }
                (slideIntoContainer(direction, tween(400)) + fadeIn(tween(400))).togetherWith(
                    slideOutOfContainer(direction, tween(400)) + fadeOut(tween(400))
                )
            },
            modifier = Modifier.fillMaxSize(),
            label = "main-tabs",
        ) { tab ->
            when (tab) {
                AppTab.Dashboard -> DashboardScreen(onStartWorkout = onStartWorkout)
                AppTab.Training -> TrainingScreen(onStartWorkout = onStartWorkout)
                AppTab.Nutrition -> NutritionScreen()
                AppTab.Insights -> InsightsScreen()
            }
        }

        TopHeader(
            onLoginClick = onShowLogin,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        AiAssistantPopup(
            visible = isAiCardOpen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 108.dp),
        )
        DarkBottomNavigationBar(
            activeTab = activeTab,
            isAiActive = isAiCardOpen,
            onTabChange = onTabChange,
            onToggleAi = onToggleAi,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopHeader(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hidden in the new dark theme design
}

@Composable
private fun HeaderLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Not needed in new dark theme
}

@Composable
private fun DarkBottomNavigationBar(
    activeTab: AppTab,
    isAiActive: Boolean,
    onTabChange: (AppTab) -> Unit,
    onToggleAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            val tabs = listOf(
                AppTab.Dashboard to FitIcon.Home,
                AppTab.Insights to FitIcon.Calendar,
                AppTab.Training to FitIcon.Dumbbell,
                AppTab.Nutrition to FitIcon.Crown,
            )

            Row(
                modifier = Modifier
                    .width(286.dp)
                    .height(64.dp)
                    .shadow(24.dp, RoundedCornerShape(34.dp), clip = false)
                    .clip(RoundedCornerShape(34.dp))
                    .background(Color(0xFF24221D).copy(alpha = 0.96f))
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, (tab, icon) ->
                    val selected = activeTab == tab
                    val animatedSize by animateDpAsState(
                        targetValue = if (selected) 42.dp else 36.dp,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "tabSize"
                    )
                    val animatedBgColor by animateColorAsState(
                        targetValue = if (selected) Mint.copy(alpha = 0.18f) else Color.Transparent,
                        animationSpec = tween(300),
                        label = "tabBg"
                    )
                    val animatedTintColor by animateColorAsState(
                        targetValue = if (selected) Mint else Color(0xFF85827C),
                        animationSpec = tween(300),
                        label = "tabTint"
                    )

                    Box(
                        modifier = Modifier
                            .size(animatedSize)
                            .clip(CircleShape)
                            .background(animatedBgColor)
                            .clickable { onTabChange(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(
                            icon = icon,
                            tint = animatedTintColor,
                            size = 22.dp,
                        )
                    }

                    if (index == 1) {
                        val aiBgColor by animateColorAsState(
                            targetValue = if (isAiActive) Mint.copy(alpha = 0.18f) else Color.Transparent,
                            animationSpec = tween(300),
                            label = "aiTabBg",
                        )
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(aiBgColor)
                                .clickable(onClick = onToggleAi),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.neurafit_logo),
                                contentDescription = "AI 助手",
                                modifier = Modifier.size(22.dp),
                                colorFilter = ColorFilter.tint(
                                    if (isAiActive) Mint else Color.White.copy(alpha = 0.72f),
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(4.dp)
                    .clip(PillShape)
                    .background(Color.White.copy(alpha = 0.88f)),
            )
        }
    }
}

@Composable
private fun AiAssistantPopup(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.96f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF17181D))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF6366F1),
                                            Color(0xFFA855F7),
                                            Color(0xFFEC4899),
                                        ),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.neurafit_logo),
                                contentDescription = "AI",
                                modifier = Modifier.size(20.dp),
                                colorFilter = ColorFilter.tint(Color.White),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "neurafitOS Assistant",
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.sp,
                            )
                            Text(
                                text = "我在这里，可以帮你打开功能和回答问题。",
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.sp,
                            )
                        }
                    }
                    AiMessageBubble(
                        text = "可以试试这些指令：打开训练、查看天气、开始 Motion Coach。",
                    )
                    AiSuggestionRow(
                        suggestions = listOf("打开训练", "看下天气", "开启 Motion Coach"),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(14.dp),
                            )
                            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "例如：播放周杰伦的稻香",
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.42f),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "AI",
                                color = Color.White.copy(alpha = 0.86f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMessageBubble(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun AiSuggestionRow(suggestions: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { suggestion ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = suggestion,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class WorkoutLaunchMode {
    Local,
    MotionCoachStream,
}

@Composable
private fun WorkoutLaunchModePicker(
    actionName: String,
    onTrainOnPhone: () -> Unit,
    onStreamToMotionCoach: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950.copy(alpha = 0.62f))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(Color.White)
                .border(1.dp, Zinc200, CardShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoText(text = "开始训练", color = Zinc500, size = 11, weight = FontWeight.ExtraBold)
                Text(
                    text = "选择训练设备",
                    color = Zinc950,
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = actionName.toWorkoutLabel(),
                    color = Zinc500,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WorkoutLaunchModeOption(
                icon = FitIcon.Camera,
                title = "在手机上训练",
                description = "使用手机摄像头进行本地计数和动作矫正。",
                onClick = onTrainOnPhone,
            )
            WorkoutLaunchModeOption(
                icon = FitIcon.Radar,
                title = "投屏至 Motion Coach",
                description = "将摄像头画面和姿态矫正数据发送至 Mac。",
                onClick = onStreamToMotionCoach,
            )
            OutlineButton(
                text = "取消",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun WorkoutLaunchModeOption(
    icon: FitIcon,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallCardShape)
            .background(Zinc50)
            .border(1.dp, Zinc200, SmallCardShape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(PillShape)
                .background(Zinc950),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(icon = icon, tint = Lime, size = 22.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = Zinc950,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                color = Zinc500,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LineIcon(icon = FitIcon.ArrowRight, tint = Zinc950, size = 18.dp)
    }
}

private fun String.toWorkoutLabel(): String =
    split("_")
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { char -> char.titlecase() }
        }
