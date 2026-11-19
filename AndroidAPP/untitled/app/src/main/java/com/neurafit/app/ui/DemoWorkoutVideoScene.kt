package com.neurafit.app.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.neurafit.app.R
import com.neurafit.app.pose.PoseLandmarkerHelper
import com.neurafit.app.pose.PoseOverlayResult
import com.neurafit.app.realtime.RealtimeVideoFrameSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val DEMO_FRAME_DELAY_MS = 40L
private const val DEMO_FRAME_INTERVAL_US = 40_000L

@Composable
fun DemoWorkoutVideoScene(
    onPoseResult: (PoseOverlayResult?) -> Unit,
    onError: (Throwable) -> Unit,
    realtimeVideoFrameSink: RealtimeVideoFrameSink? = null,
) {
    val context = LocalContext.current
    var currentFrame by remember { mutableStateOf<Bitmap?>(null) }
    val poseLandmarkerHelper = remember(context) {
        PoseLandmarkerHelper(
            context = context.applicationContext,
            onPoseResult = onPoseResult,
        )
    }

    LaunchedEffect(onPoseResult) {
        poseLandmarkerHelper.onPoseResult = onPoseResult
    }

    DisposableEffect(poseLandmarkerHelper) {
        onDispose {
            poseLandmarkerHelper.close()
        }
    }

    LaunchedEffect(poseLandmarkerHelper, realtimeVideoFrameSink) {
        val retriever = MediaMetadataRetriever()
        try {
            val videoUri = Uri.parse("android.resource://${context.packageName}/${R.raw.workout_demo}")
            retriever.setDataSource(context, videoUri)
            val durationUs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.times(1_000L)
                ?.coerceAtLeast(DEMO_FRAME_INTERVAL_US)
                ?: DEMO_FRAME_INTERVAL_US
            var frameTimeUs = 0L

            while (isActive) {
                val frame = withContext(Dispatchers.Default) {
                    val extractedFrame = retriever.getFrameAtTime(
                        frameTimeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                    )
                    if (extractedFrame != null) {
                        poseLandmarkerHelper.detectBitmapLiveStream(extractedFrame)
                        realtimeVideoFrameSink?.onCameraFrame(
                            bitmap = extractedFrame,
                            rotationDegrees = 0,
                            mirrorHorizontally = false,
                            timestampNs = System.nanoTime(),
                        )
                    }
                    extractedFrame
                }
                if (frame != null) {
                    currentFrame = frame
                }
                frameTimeUs += DEMO_FRAME_INTERVAL_US
                if (frameTimeUs >= durationUs) {
                    frameTimeUs = 0L
                }
                delay(DEMO_FRAME_DELAY_MS)
            }
        } catch (throwable: Throwable) {
            onError(throwable)
        } finally {
            retriever.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950),
        contentAlignment = Alignment.Center,
    ) {
        val frame = currentFrame
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Demo workout video",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = "Loading workout video...",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
        }
    }
}
