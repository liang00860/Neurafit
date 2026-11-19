package com.neurafit.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ConcurrentHashMap

class PoseLandmarkerHelper(
    context: Context,
    var onPoseResult: (PoseOverlayResult?) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingFrames = ConcurrentHashMap<Long, FrameInfo>()
    private var poseLandmarker: PoseLandmarker? = null
    private var lastTimestampMs = 0L

    init {
        setupPoseLandmarker()
    }

    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean,
    ) {
        try {
            val landmarker = poseLandmarker ?: return
            val bitmap = imageProxy.copyToBitmap()
            detectBitmapLiveStream(
                bitmap = bitmap,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                mirrorHorizontally = isFrontCamera,
                landmarker = landmarker,
            )
        } catch (throwable: Throwable) {
            Log.e(TAG, "Pose detection failed.", throwable)
        } finally {
            imageProxy.close()
        }
    }

    fun detectBitmapLiveStream(
        bitmap: Bitmap,
        mirrorHorizontally: Boolean = false,
        rotationDegrees: Int = 0,
    ) {
        try {
            val landmarker = poseLandmarker ?: return
            detectBitmapLiveStream(
                bitmap = bitmap,
                rotationDegrees = rotationDegrees,
                mirrorHorizontally = mirrorHorizontally,
                landmarker = landmarker,
            )
        } catch (throwable: Throwable) {
            Log.e(TAG, "Pose detection failed for demo video frame.", throwable)
        }
    }

    private fun detectBitmapLiveStream(
        bitmap: Bitmap,
        rotationDegrees: Int,
        mirrorHorizontally: Boolean,
        landmarker: PoseLandmarker,
    ) {
        val rotatedBitmap = bitmap.rotateAndMirror(
            rotationDegrees = rotationDegrees,
            mirrorHorizontally = mirrorHorizontally,
        )
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        val timestampMs = nextTimestampMs()
        pendingFrames[timestampMs] = FrameInfo(
            width = rotatedBitmap.width,
            height = rotatedBitmap.height,
        )
        landmarker.detectAsync(mpImage, timestampMs)
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
        pendingFrames.clear()
        dispatchPoseResult(null)
    }

    private fun setupPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(PoseLandmarkerConfig.MODEL_ASSET_PATH)
                .build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setResultListener(::handlePoseResult)
                .setErrorListener { error ->
                    Log.e(TAG, "Pose landmarker runtime error.", error)
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(appContext, options)
        } catch (throwable: Throwable) {
            Log.e(
                TAG,
                "Unable to initialize Pose Landmarker. Confirm ${PoseLandmarkerConfig.MODEL_ASSET_PATH} exists in app/src/main/assets.",
                throwable,
            )
            poseLandmarker = null
        }
    }

    private fun handlePoseResult(
        result: PoseLandmarkerResult,
        @Suppress("UNUSED_PARAMETER") input: MPImage,
    ) {
        val poseCount = result.landmarks().size
        val landmarkCount = result.landmarks().sumOf { landmarks -> landmarks.size }
        Log.d(
            TAG,
            "poseDetected=${poseCount > 0}, poseCount=$poseCount, landmarkCount=$landmarkCount",
        )

        cleanupOldFrames(currentTimestampMs = result.timestampMs())
        val frameInfo = pendingFrames.remove(result.timestampMs())
        val firstPose = result.landmarks().firstOrNull()
        if (frameInfo == null || firstPose.isNullOrEmpty()) {
            dispatchPoseResult(null)
            return
        }

        val landmarkPoints = firstPose.mapIndexedNotNull { index, landmark ->
            val poseIndex = PoseIndex.fromValue(index) ?: return@mapIndexedNotNull null
            PoseLandmarkPoint(
                index = poseIndex,
                x = landmark.x(),
                y = landmark.y(),
                visibility = landmark.visibility().orElse(1f),
                presence = landmark.presence().orElse(1f),
            )
        }

        dispatchPoseResult(
            PoseOverlayResult(
                landmarks = landmarkPoints,
                imageWidth = frameInfo.width,
                imageHeight = frameInfo.height,
            ),
        )
    }

    private fun nextTimestampMs(): Long {
        val now = SystemClock.uptimeMillis()
        val timestampMs = if (now > lastTimestampMs) now else lastTimestampMs + 1L
        lastTimestampMs = timestampMs
        return timestampMs
    }

    private fun cleanupOldFrames(currentTimestampMs: Long) {
        pendingFrames.entries.removeIf { (timestampMs, _) ->
            timestampMs < currentTimestampMs - FRAME_INFO_RETENTION_MS
        }
    }

    private fun dispatchPoseResult(result: PoseOverlayResult?) {
        mainHandler.post {
            onPoseResult(result)
        }
    }

    private fun ImageProxy.copyToBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        planes[0].buffer.rewind()
        bitmap.copyPixelsFromBuffer(planes[0].buffer)
        return bitmap
    }

    private fun Bitmap.rotateAndMirror(
        rotationDegrees: Int,
        mirrorHorizontally: Boolean,
    ): Bitmap {
        if (rotationDegrees == 0 && !mirrorHorizontally) return this

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (mirrorHorizontally) {
                postScale(-1f, 1f)
            }
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private companion object {
        const val TAG = "PoseLandmarkerHelper"
        const val FRAME_INFO_RETENTION_MS = 1_000L
    }

    private data class FrameInfo(
        val width: Int,
        val height: Int,
    )
}
