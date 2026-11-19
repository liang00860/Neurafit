package com.neurafit.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.pose.PoseIndex
import com.neurafit.app.pose.PoseLandmarkPoint
import com.neurafit.app.pose.PoseOverlayResult

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var poseResult: PoseOverlayResult? = null
    private var showLandmarkIndex: Boolean = false
    private var errorParts: Set<PoseBodyPart> = emptySet()
    private var errorTypes: Set<ActionErrorType> = emptySet()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SKELETON_COLOR
        strokeWidth = 7f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val errorLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ERROR_COLOR
        strokeWidth = 9f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LANDMARK_COLOR
        style = Paint.Style.FILL
    }
    private val errorLandmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ERROR_COLOR
        style = Paint.Style.FILL
    }
    private val landmarkStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }
    private val indexTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    fun updatePoseResult(
        poseResult: PoseOverlayResult?,
        showLandmarkIndex: Boolean = this.showLandmarkIndex,
        errorParts: Set<PoseBodyPart> = this.errorParts,
        errorTypes: Set<ActionErrorType> = this.errorTypes,
    ) {
        this.poseResult = poseResult
        this.showLandmarkIndex = showLandmarkIndex
        this.errorParts = errorParts
        this.errorTypes = errorTypes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val result = poseResult ?: return
        if (result.imageWidth <= 0 || result.imageHeight <= 0) return

        val visibleLandmarks = result.landmarks
            .filter { it.confidence() >= MIN_CONFIDENCE }
            .associateBy { it.index }
        val errorLandmarks = errorLandmarkIndexes()
        val transform = CoordinateTransform(
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
            imageWidth = result.imageWidth.toFloat(),
            imageHeight = result.imageHeight.toFloat(),
        )

        SKELETON_CONNECTIONS.forEach { connection ->
            val start = visibleLandmarks[connection.start] ?: return@forEach
            val end = visibleLandmarks[connection.end] ?: return@forEach
            val startPoint = transform.map(start)
            val endPoint = transform.map(end)
            canvas.drawLine(
                startPoint.x,
                startPoint.y,
                endPoint.x,
                endPoint.y,
                if (connection.shouldHighlight()) errorLinePaint else linePaint,
            )
        }

        visibleLandmarks.values.forEach { landmark ->
            val point = transform.map(landmark)
            val paint = if (landmark.index in errorLandmarks) errorLandmarkPaint else landmarkPaint
            canvas.drawCircle(point.x, point.y, LANDMARK_RADIUS, paint)
            canvas.drawCircle(point.x, point.y, LANDMARK_RADIUS, landmarkStrokePaint)
            if (showLandmarkIndex) {
                canvas.drawText(
                    landmark.index.value.toString(),
                    point.x + LANDMARK_RADIUS + 4f,
                    point.y - LANDMARK_RADIUS,
                    indexTextPaint,
                )
            }
        }
    }

    private fun PoseConnection.shouldHighlight(): Boolean {
        if (part in errorParts) {
            return true
        }
        return errorTypes.any { errorType ->
            when (errorType) {
                ActionErrorType.NONE -> false
                ActionErrorType.KNEE_VALGUS -> part == PoseBodyPart.LEFT_LEG || part == PoseBodyPart.RIGHT_LEG
                ActionErrorType.BACK_LEAN_TOO_MUCH -> isTorsoSideLine()
                ActionErrorType.SQUAT_TOO_SHALLOW -> isHipToKneeLine()
                ActionErrorType.PUSH_UP_TOO_SHALLOW -> part == PoseBodyPart.LEFT_ARM || part == PoseBodyPart.RIGHT_ARM
                ActionErrorType.WAIST_COLLAPSE,
                ActionErrorType.HIPS_TOO_HIGH -> isTorsoSideLine() ||
                    part == PoseBodyPart.LEFT_LEG ||
                    part == PoseBodyPart.RIGHT_LEG
                ActionErrorType.PUSH_UP_TOO_FAST -> part == PoseBodyPart.LEFT_ARM ||
                    part == PoseBodyPart.RIGHT_ARM ||
                    isTorsoSideLine()
            }
        }
    }

    private fun errorLandmarkIndexes(): Set<PoseIndex> {
        val indexes = mutableSetOf<PoseIndex>()
        if (ActionErrorType.KNEE_VALGUS in errorTypes) {
            indexes += PoseIndex.LEFT_KNEE
            indexes += PoseIndex.RIGHT_KNEE
        }
        if (ActionErrorType.BACK_LEAN_TOO_MUCH in errorTypes) {
            indexes += PoseIndex.LEFT_SHOULDER
            indexes += PoseIndex.RIGHT_SHOULDER
            indexes += PoseIndex.LEFT_HIP
            indexes += PoseIndex.RIGHT_HIP
        }
        if (ActionErrorType.SQUAT_TOO_SHALLOW in errorTypes) {
            indexes += PoseIndex.LEFT_HIP
            indexes += PoseIndex.RIGHT_HIP
            indexes += PoseIndex.LEFT_KNEE
            indexes += PoseIndex.RIGHT_KNEE
        }
        if (ActionErrorType.PUSH_UP_TOO_SHALLOW in errorTypes || ActionErrorType.PUSH_UP_TOO_FAST in errorTypes) {
            indexes += PoseIndex.LEFT_SHOULDER
            indexes += PoseIndex.RIGHT_SHOULDER
            indexes += PoseIndex.LEFT_ELBOW
            indexes += PoseIndex.RIGHT_ELBOW
            indexes += PoseIndex.LEFT_WRIST
            indexes += PoseIndex.RIGHT_WRIST
        }
        if (ActionErrorType.WAIST_COLLAPSE in errorTypes || ActionErrorType.HIPS_TOO_HIGH in errorTypes) {
            indexes += PoseIndex.LEFT_SHOULDER
            indexes += PoseIndex.RIGHT_SHOULDER
            indexes += PoseIndex.LEFT_HIP
            indexes += PoseIndex.RIGHT_HIP
            indexes += PoseIndex.LEFT_ANKLE
            indexes += PoseIndex.RIGHT_ANKLE
        }
        return indexes
    }

    private data class CoordinateTransform(
        val viewWidth: Float,
        val viewHeight: Float,
        val imageWidth: Float,
        val imageHeight: Float,
    ) {
        private val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
        private val scaledImageWidth = imageWidth * scale
        private val scaledImageHeight = imageHeight * scale
        private val offsetX = (viewWidth - scaledImageWidth) / 2f
        private val offsetY = (viewHeight - scaledImageHeight) / 2f

        fun map(landmark: PoseLandmarkPoint): ScreenPoint =
            ScreenPoint(
                x = landmark.x * imageWidth * scale + offsetX,
                y = landmark.y * imageHeight * scale + offsetY,
            )
    }

    private data class ScreenPoint(
        val x: Float,
        val y: Float,
    )

    enum class PoseBodyPart {
        HEAD,
        TORSO,
        LEFT_ARM,
        RIGHT_ARM,
        LEFT_HAND,
        RIGHT_HAND,
        LEFT_LEG,
        RIGHT_LEG,
        LEFT_FOOT,
        RIGHT_FOOT,
    }

    private data class PoseConnection(
        val start: PoseIndex,
        val end: PoseIndex,
        val part: PoseBodyPart,
    ) {
        fun connects(first: PoseIndex, second: PoseIndex): Boolean =
            (start == first && end == second) || (start == second && end == first)

        fun isTorsoSideLine(): Boolean =
            connects(PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_HIP) ||
                connects(PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_HIP)

        fun isHipToKneeLine(): Boolean =
            connects(PoseIndex.LEFT_HIP, PoseIndex.LEFT_KNEE) ||
                connects(PoseIndex.RIGHT_HIP, PoseIndex.RIGHT_KNEE)
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.45f
        const val SKELETON_COLOR = 0xFFC6EA7E.toInt()
        const val LANDMARK_COLOR = 0xFF0EA5E9.toInt()
        const val ERROR_COLOR = 0xFFE11D48.toInt()
        const val LANDMARK_RADIUS = 7.5f

        val SKELETON_CONNECTIONS = listOf(
            PoseConnection(PoseIndex.NOSE, PoseIndex.LEFT_EYE_INNER, PoseBodyPart.HEAD),
            PoseConnection(PoseIndex.NOSE, PoseIndex.RIGHT_EYE_INNER, PoseBodyPart.HEAD),
            PoseConnection(PoseIndex.LEFT_EYE_INNER, PoseIndex.LEFT_EYE, PoseBodyPart.HEAD),
            PoseConnection(PoseIndex.LEFT_EYE, PoseIndex.LEFT_EYE_OUTER, PoseBodyPart.HEAD),
            PoseConnection(PoseIndex.RIGHT_EYE_INNER, PoseIndex.RIGHT_EYE, PoseBodyPart.HEAD),
            PoseConnection(PoseIndex.RIGHT_EYE, PoseIndex.RIGHT_EYE_OUTER, PoseBodyPart.HEAD),
            PoseConnection(PoseIndex.LEFT_EYE_OUTER, PoseIndex.LEFT_EAR, PoseBodyPart.HEAD),
            PoseConnection(PoseIndex.RIGHT_EYE_OUTER, PoseIndex.RIGHT_EAR, PoseBodyPart.HEAD),
            PoseConnection(PoseIndex.MOUTH_LEFT, PoseIndex.MOUTH_RIGHT, PoseBodyPart.HEAD),
            PoseConnection(PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER, PoseBodyPart.TORSO),
            PoseConnection(PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_HIP, PoseBodyPart.TORSO),
            PoseConnection(PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_HIP, PoseBodyPart.TORSO),
            PoseConnection(PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP, PoseBodyPart.TORSO),
            PoseConnection(PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_ELBOW, PoseBodyPart.LEFT_ARM),
            PoseConnection(PoseIndex.LEFT_ELBOW, PoseIndex.LEFT_WRIST, PoseBodyPart.LEFT_ARM),
            PoseConnection(PoseIndex.LEFT_WRIST, PoseIndex.LEFT_PINKY, PoseBodyPart.LEFT_HAND),
            PoseConnection(PoseIndex.LEFT_WRIST, PoseIndex.LEFT_INDEX, PoseBodyPart.LEFT_HAND),
            PoseConnection(PoseIndex.LEFT_WRIST, PoseIndex.LEFT_THUMB, PoseBodyPart.LEFT_HAND),
            PoseConnection(PoseIndex.LEFT_PINKY, PoseIndex.LEFT_INDEX, PoseBodyPart.LEFT_HAND),
            PoseConnection(PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_ELBOW, PoseBodyPart.RIGHT_ARM),
            PoseConnection(PoseIndex.RIGHT_ELBOW, PoseIndex.RIGHT_WRIST, PoseBodyPart.RIGHT_ARM),
            PoseConnection(PoseIndex.RIGHT_WRIST, PoseIndex.RIGHT_PINKY, PoseBodyPart.RIGHT_HAND),
            PoseConnection(PoseIndex.RIGHT_WRIST, PoseIndex.RIGHT_INDEX, PoseBodyPart.RIGHT_HAND),
            PoseConnection(PoseIndex.RIGHT_WRIST, PoseIndex.RIGHT_THUMB, PoseBodyPart.RIGHT_HAND),
            PoseConnection(PoseIndex.RIGHT_PINKY, PoseIndex.RIGHT_INDEX, PoseBodyPart.RIGHT_HAND),
            PoseConnection(PoseIndex.LEFT_HIP, PoseIndex.LEFT_KNEE, PoseBodyPart.LEFT_LEG),
            PoseConnection(PoseIndex.LEFT_KNEE, PoseIndex.LEFT_ANKLE, PoseBodyPart.LEFT_LEG),
            PoseConnection(PoseIndex.LEFT_ANKLE, PoseIndex.LEFT_HEEL, PoseBodyPart.LEFT_FOOT),
            PoseConnection(PoseIndex.LEFT_HEEL, PoseIndex.LEFT_FOOT_INDEX, PoseBodyPart.LEFT_FOOT),
            PoseConnection(PoseIndex.LEFT_ANKLE, PoseIndex.LEFT_FOOT_INDEX, PoseBodyPart.LEFT_FOOT),
            PoseConnection(PoseIndex.RIGHT_HIP, PoseIndex.RIGHT_KNEE, PoseBodyPart.RIGHT_LEG),
            PoseConnection(PoseIndex.RIGHT_KNEE, PoseIndex.RIGHT_ANKLE, PoseBodyPart.RIGHT_LEG),
            PoseConnection(PoseIndex.RIGHT_ANKLE, PoseIndex.RIGHT_HEEL, PoseBodyPart.RIGHT_FOOT),
            PoseConnection(PoseIndex.RIGHT_HEEL, PoseIndex.RIGHT_FOOT_INDEX, PoseBodyPart.RIGHT_FOOT),
            PoseConnection(PoseIndex.RIGHT_ANKLE, PoseIndex.RIGHT_FOOT_INDEX, PoseBodyPart.RIGHT_FOOT),
        )
    }
}
