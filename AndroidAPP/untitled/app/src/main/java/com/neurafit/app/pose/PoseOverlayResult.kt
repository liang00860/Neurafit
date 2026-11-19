package com.neurafit.app.pose

data class PoseOverlayResult(
    val landmarks: List<PoseLandmarkPoint>,
    val imageWidth: Int,
    val imageHeight: Int,
)

data class PoseLandmarkPoint(
    val index: PoseIndex,
    val x: Float,
    val y: Float,
    val visibility: Float,
    val presence: Float,
) {
    fun confidence(): Float = minOf(visibility, presence)
}
