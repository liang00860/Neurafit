package com.neurafit.app.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.neurafit.app.BuildConfig
import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.CorrectionResult
import com.neurafit.app.analysis.MotionCorrectionEngine
import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.camera.CameraController
import com.neurafit.app.pose.PoseOverlayResult
import com.neurafit.app.realtime.PoseFramePayloadMapper
import com.neurafit.app.realtime.RealtimeConnectionState
import com.neurafit.app.realtime.RealtimeSessionManager
import com.neurafit.app.realtime.RealtimeVideoFrameSink

@Composable
fun MotionCoachStreamingScreen(
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
    val realtimeSessionManager = remember(context) {
        RealtimeSessionManager(
            context = context.applicationContext,
            roomId = DEFAULT_REALTIME_ROOM_ID,
            signalingUrl = BuildConfig.SIGNALING_URL,
            iceServersJson = BuildConfig.RTC_ICE_SERVERS,
            forceRelay = BuildConfig.RTC_FORCE_RELAY,
        )
    }
    val engineState by motionEngine.state.collectAsState()
    val realtimeState by realtimeSessionManager.state.collectAsState()
    val streamMirrored = !BuildConfig.USE_DEMO_WORKOUT_VIDEO
    var hasCameraPermission by remember {
        mutableStateOf(context.hasCameraPermission())
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var realtimeFrameId by remember { mutableStateOf(0L) }
    val onPoseResult by rememberUpdatedState<(PoseOverlayResult?) -> Unit> { result ->
        motionEngine.processPoseResult(result)
        realtimeFrameId += 1
        realtimeSessionManager.sendPoseFrame(
            PoseFramePayloadMapper.toJsonString(
                state = motionEngine.state.value,
                frameId = realtimeFrameId,
                mirrored = streamMirrored,
            ),
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        cameraError = if (granted) {
            null
        } else {
            "Camera permission is required to stream to Motion Coach."
        }
    }

    LaunchedEffect(motionEngine) {
        motionEngine.startTraining()
    }

    LaunchedEffect(realtimeSessionManager, hasCameraPermission) {
        if (BuildConfig.USE_DEMO_WORKOUT_VIDEO || hasCameraPermission) {
            realtimeSessionManager.connect()
        }
    }

    LaunchedEffect(Unit) {
        if (!BuildConfig.USE_DEMO_WORKOUT_VIDEO && !hasCameraPermission) {
            permissionLauncher.launch(CameraController.CAMERA_PERMISSION)
        }
    }

    fun finishTrainingAndClose() {
        realtimeSessionManager.disconnect()
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

    DisposableEffect(realtimeSessionManager) {
        onDispose {
            realtimeSessionManager.close()
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
                realtimeVideoFrameSink = realtimeSessionManager,
                onError = { throwable ->
                    cameraError = throwable.localizedMessage ?: "Unable to load demo workout video stream."
                },
            )
        } else if (hasCameraPermission) {
            StreamingCameraPreview(
                cameraController = cameraController,
                lifecycleOwner = lifecycleOwner,
                onPoseResult = onPoseResult,
                realtimeVideoFrameSink = realtimeSessionManager,
                onError = { throwable ->
                    cameraError = throwable.localizedMessage ?: "Unable to start camera stream."
                },
            )
        } else {
            StreamingPermissionNotice(
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
                        0f to Zinc950.copy(alpha = 0.10f),
                        0.58f to Color.Transparent,
                        1f to Zinc950.copy(alpha = 0.90f),
                    ),
                ),
        )

        StreamingPoseOverlay(
            poseResult = engineState.poseOverlayResult,
            errorTypes = engineState.correctionResult.errors,
            modifier = Modifier.matchParentSize(),
        )

        StreamingStatusLayer(
            realtimeState = realtimeState,
            actionName = engineState.actionName,
            repCount = if (engineState.actionName == WorkoutActions.PUSH_UP) {
                engineState.pushUpState.repetitionCount
            } else {
                engineState.squatState.repetitionCount
            },
            onReconnect = { realtimeSessionManager.connect() },
            modifier = Modifier.matchParentSize(),
        )

        (cameraError ?: engineState.errorMessage)?.let { message ->
            StreamingCameraErrorNotice(
                message = message,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        StreamingCorrectionBanner(
            result = engineState.correctionResult,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 132.dp),
        )

        StreamingControls(
            onClose = ::finishTrainingAndClose,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun StreamingCameraPreview(
    cameraController: CameraController,
    lifecycleOwner: LifecycleOwner,
    onPoseResult: (PoseOverlayResult?) -> Unit,
    realtimeVideoFrameSink: RealtimeVideoFrameSink,
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
private fun StreamingPoseOverlay(
    poseResult: PoseOverlayResult?,
    errorTypes: Set<ActionErrorType>,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PoseOverlayView(viewContext)
        },
        update = { overlayView ->
            overlayView.updatePoseResult(
                poseResult = poseResult,
                showLandmarkIndex = false,
                errorTypes = errorTypes,
            )
        },
    )
}

@Composable
private fun StreamingStatusLayer(
    realtimeState: RealtimeConnectionState,
    actionName: String,
    repCount: Int,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < 460.dp
        StreamingStatusPanel(
            state = realtimeState,
            actionName = actionName,
            repCount = repCount,
            onReconnect = onReconnect,
            modifier = if (compact) {
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 44.dp)
                    .fillMaxWidth()
            } else {
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp, top = 48.dp)
                    .width(292.dp)
            },
        )
    }
}

@Composable
private fun StreamingStatusPanel(
    state: RealtimeConnectionState,
    actionName: String,
    repCount: Int,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(Zinc950.copy(alpha = 0.62f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        MonoText(text = "Motion Coach Stream", color = Lime, size = 11, weight = FontWeight.ExtraBold)
        Text(
            text = actionName.toStreamingWorkoutLabel(),
            color = Color.White,
            fontSize = 26.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StreamingMetricRow("Room", state.roomId)
        StreamingMetricRow("Signal", state.signalingState)
        StreamingMetricRow("Peer", state.peerState)
        StreamingMetricRow("Channel", state.dataChannelState)
        StreamingMetricRow("Reps", repCount.toString(), valueColor = Lime)
        Text(
            text = state.signalingUrl,
            color = Zinc300,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        state.errorMessage?.let { error ->
            Text(
                text = error,
                color = Orange,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PrimaryPillButton(
            text = if (state.enabled) "Reconnect" else "Connect",
            icon = FitIcon.Radar,
            background = Lime,
            color = Zinc950,
            modifier = Modifier.fillMaxWidth(),
            onClick = onReconnect,
        )
    }
}

@Composable
private fun StreamingMetricRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Zinc300,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StreamingCorrectionBanner(
    result: CorrectionResult,
    modifier: Modifier = Modifier,
) {
    val hasError = result.hasStreamingActionError()
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
private fun StreamingPermissionNotice(
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
                text = "Camera access is needed to stream to Motion Coach.",
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

@Composable
private fun StreamingCameraErrorNotice(
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
private fun StreamingControls(
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
            .padding(horizontal = 24.dp, vertical = 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconCircleButton(
            icon = FitIcon.Stop,
            size = 78.dp,
            iconSize = 31.dp,
            background = Color(0xFFDC2626),
            border = Color(0xFFDC2626),
            onClick = onClose,
        )
    }
}

private fun CorrectionResult.hasStreamingActionError(): Boolean =
    errors.any { it != ActionErrorType.NONE }

private fun String.toStreamingWorkoutLabel(): String =
    split("_")
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { char -> char.titlecase() }
        }

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        CameraController.CAMERA_PERMISSION,
    ) == PackageManager.PERMISSION_GRANTED

private const val DEFAULT_REALTIME_ROOM_ID = "motion-2026"
