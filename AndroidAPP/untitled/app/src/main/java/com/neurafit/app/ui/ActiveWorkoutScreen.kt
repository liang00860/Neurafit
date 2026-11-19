package com.neurafit.app.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.CorrectionResult
import com.neurafit.app.analysis.MotionCorrectionEngine
import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.BuildConfig
import com.neurafit.app.camera.CameraController
import com.neurafit.app.pose.PoseOverlayResult
import com.neurafit.app.realtime.RealtimeVideoFrameSink
import kotlinx.coroutines.delay

private const val HYDRATION_REMINDER_THRESHOLD_MS = 10 * 60 * 1000L
@Composable
fun ActiveWorkoutScreen(
    actionName: String = WorkoutActions.SQUAT,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember(context) {
        CameraController(context.applicationContext)
    }
    val motionEngine = remember(context) {
        MotionCorrectionEngine.create(
            context = context.applicationContext,
            actionName = actionName,
        )
    }
    val engineState by motionEngine.state.collectAsState()
    var hasCameraPermission by remember {
        mutableStateOf(context.hasCameraPermission())
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showHydrationReminder by rememberSaveable { mutableStateOf(false) }
    var hydrationReminderDismissed by rememberSaveable { mutableStateOf(false) }
    val onPoseResult by rememberUpdatedState<(PoseOverlayResult?) -> Unit> { result ->
        motionEngine.processPoseResult(result)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        cameraError = if (granted) {
            null
        } else {
            "Camera permission is required for live training."
        }
    }

    LaunchedEffect(motionEngine) {
        motionEngine.startTraining()
    }

    LaunchedEffect(engineState.sessionStartedAtMs) {
        showHydrationReminder = false
        hydrationReminderDismissed = false
    }

    LaunchedEffect(
        engineState.isTrainingStarted,
        engineState.sessionStartedAtMs,
        hydrationReminderDismissed,
    ) {
        val sessionStartedAtMs = engineState.sessionStartedAtMs ?: run {
            showHydrationReminder = false
            return@LaunchedEffect
        }
        if (!engineState.isTrainingStarted || hydrationReminderDismissed) {
            if (!engineState.isTrainingStarted) {
                showHydrationReminder = false
            }
            return@LaunchedEffect
        }

        while (engineState.isTrainingStarted && !hydrationReminderDismissed) {
            if (System.currentTimeMillis() - sessionStartedAtMs >= HYDRATION_REMINDER_THRESHOLD_MS) {
                showHydrationReminder = true
                break
            }
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        if (!BuildConfig.USE_DEMO_WORKOUT_VIDEO && !hasCameraPermission) {
            permissionLauncher.launch(CameraController.CAMERA_PERMISSION)
        }
    }

    fun finishTrainingAndClose() {
        motionEngine.finishTraining(onFinished = onClose)
    }

    DisposableEffect(cameraController) {
        onDispose {
            cameraController.stopCamera()
        }
    }

    DisposableEffect(motionEngine) {
        onDispose {
            motionEngine.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950),
    ) {
        if (BuildConfig.USE_DEMO_WORKOUT_VIDEO) {
            DemoWorkoutVideoScene(
                onPoseResult = onPoseResult,
                onError = { throwable ->
                    cameraError = throwable.localizedMessage ?: "Unable to load demo workout video."
                },
            )
        } else if (hasCameraPermission) {
            CameraPreview(
                cameraController = cameraController,
                lifecycleOwner = lifecycleOwner,
                onPoseResult = onPoseResult,
                realtimeVideoFrameSink = null,
                onError = { throwable ->
                    cameraError = throwable.localizedMessage ?: "Unable to start camera preview."
                },
            )
        } else {
            PermissionNotice(
                onRequestPermission = {
                    permissionLauncher.launch(CameraController.CAMERA_PERMISSION)
                },
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Zinc950.copy(alpha = 0.05f),
                        0.55f to Color.Transparent,
                        1f to Zinc950.copy(alpha = 0.88f),
                    ),
                ),
        )

        PoseOverlay(
            poseResult = engineState.poseOverlayResult,
            showLandmarkIndex = false,
            errorTypes = engineState.correctionResult.errors,
            modifier = Modifier.matchParentSize(),
        )
        WorkoutTopHud(
            actionName = engineState.actionName,
            repCount = if (engineState.actionName == WorkoutActions.PUSH_UP) {
                engineState.pushUpState.repetitionCount
            } else {
                engineState.squatState.repetitionCount
            },
        )
        (cameraError ?: engineState.errorMessage)?.let { message ->
            CameraErrorNotice(
                message = message,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        CorrectionBanner(
            result = engineState.correctionResult,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 154.dp),
        )
        WorkoutControls(
            onClose = ::finishTrainingAndClose,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        HydrationReminderCard(
            visible = showHydrationReminder,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            onDismiss = {
                hydrationReminderDismissed = true
                showHydrationReminder = false
            },
        )
    }
}

@Composable
private fun CameraPreview(
    cameraController: CameraController,
    lifecycleOwner: LifecycleOwner,
    onPoseResult: (PoseOverlayResult?) -> Unit,
    realtimeVideoFrameSink: RealtimeVideoFrameSink?,
    onError: (Throwable) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            cameraController.startCamera(
                previewView = previewView,
                lifecycleOwner = lifecycleOwner,
                onPoseResult = onPoseResult,
                realtimeVideoFrameSink = realtimeVideoFrameSink,
                onError = onError,
            )
        },
    )
}

@Composable
private fun PoseOverlay(
    poseResult: PoseOverlayResult?,
    showLandmarkIndex: Boolean,
    modifier: Modifier = Modifier,
    errorParts: Set<PoseOverlayView.PoseBodyPart> = emptySet(),
    errorTypes: Set<ActionErrorType> = emptySet(),
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PoseOverlayView(viewContext)
        },
        update = { overlayView ->
            overlayView.updatePoseResult(
                poseResult = poseResult,
                showLandmarkIndex = showLandmarkIndex,
                errorParts = errorParts,
                errorTypes = errorTypes,
            )
        },
    )
}

@Composable
private fun CorrectionBanner(
    result: CorrectionResult,
    modifier: Modifier = Modifier,
) {
    val hasError = result.hasActionError()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                if (hasError) {
                    Color(0xFFDC2626).copy(alpha = 0.88f)
                } else {
                    Zinc950.copy(alpha = 0.58f)
                },
            )
            .border(
                1.dp,
                if (hasError) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.12f),
                CardShape,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = result.message,
            color = Color.White,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PermissionNotice(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Zinc950),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .clip(CardShape)
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CardShape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LineIcon(icon = FitIcon.Camera, tint = Lime, size = 34.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Camera access is needed to start live training.",
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp,
            )
            Spacer(Modifier.height(20.dp))
            PrimaryPillButton(
                text = "Grant Access",
                icon = FitIcon.Camera,
                background = Lime,
                color = Zinc950,
                onClick = onRequestPermission,
            )
        }
    }
}

private fun CorrectionResult.hasActionError(): Boolean =
    errors.any { it != ActionErrorType.NONE }

@Composable
private fun WorkoutTopHud(
    actionName: String,
    repCount: Int,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp),
    ) {
        val narrow = maxWidth < 440.dp
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CameraHud(actionName = actionName)
                CounterHud(actionName = actionName, repCount = repCount)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                CameraHud(
                    actionName = actionName,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(14.dp))
                CounterHud(
                    actionName = actionName,
                    repCount = repCount,
                    modifier = Modifier.weight(0.9f),
                )
            }
        }
    }
}

@Composable
private fun CameraHud(
    actionName: String,
    modifier: Modifier = Modifier,
) {
    MotionCoachGlassCard(modifier = modifier) {
        MotionCoachPill(text = "CAMERA")
        Spacer(Modifier.height(14.dp))
        Text(
            text = actionName.toActiveWorkoutLabel(),
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "实时取景",
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun CounterHud(
    actionName: String,
    repCount: Int,
    modifier: Modifier = Modifier,
) {
    MotionCoachGlassCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
            MotionCoachPill(text = "${actionName.toCounterLabel()} COUNTER")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = repCount.toString(),
                    color = Color.White,
                    fontSize = 44.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "次",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 16.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "实时计数",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            )
        }
    }
}

private fun String.toActiveWorkoutLabel(): String =
    when (this) {
        WorkoutActions.PUSH_UP -> "俯卧撑"
        else -> "深蹲"
    }

@Composable
private fun MotionCoachGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF171A17).copy(alpha = 0.62f))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.08f),
                    ),
                ),
                RoundedCornerShape(28.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

@Composable
private fun MotionCoachPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
    }
}

private fun String.toCounterLabel(): String =
    when (this) {
        WorkoutActions.PUSH_UP -> "俯卧撑"
        else -> "深蹲"
    }

@Composable
private fun CameraErrorNotice(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .clip(CardShape)
            .background(Color(0xFFDC2626).copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CardShape)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WorkoutControls(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Zinc950.copy(alpha = 0.92f)),
                ),
            )
            .padding(horizontal = 24.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.20f), PillShape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(icon = FitIcon.Pause, size = 64.dp, iconSize = 28.dp)
            IconCircleButton(
                icon = FitIcon.Stop,
                size = 78.dp,
                iconSize = 31.dp,
                background = Color(0xFFDC2626),
                border = Color(0xFFDC2626),
                onClick = onClose,
            )
            IconCircleButton(icon = FitIcon.Settings, size = 64.dp, iconSize = 30.dp)
        }
    }
}

@Composable
private fun HydrationReminderCard(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.96f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Mint)
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
                .padding(24.dp),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LineIcon(icon = FitIcon.Droplet, tint = Color.White, size = 18.dp)
                        MonoText(
                            text = "Hydration",
                            color = Color.White,
                            size = 10,
                            weight = FontWeight.ExtraBold,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "你已经连续训练超过 10 分钟",
                    color = Color.White,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "先补充一点水分，再继续训练，能帮助你更稳定地保持状态。",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(18.dp))
                PrimaryPillButton(
                    text = "知道了",
                    icon = FitIcon.Droplet,
                    background = Color.White,
                    color = Zinc950,
                    onClick = onDismiss,
                )
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        CameraController.CAMERA_PERMISSION,
    ) == PackageManager.PERMISSION_GRANTED
