package com.neurafit.app.analysis

import com.neurafit.app.pose.PoseIndex
import com.neurafit.app.pose.PoseLandmarkPoint

object BodyCenterCalculator {
    fun shoulderCenter(landmarks: Map<PoseIndex, PoseLandmarkPoint>): Point2D? =
        centerOf(landmarks, PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER)

    fun hipCenter(landmarks: Map<PoseIndex, PoseLandmarkPoint>): Point2D? =
        centerOf(landmarks, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)

    fun kneeCenter(landmarks: Map<PoseIndex, PoseLandmarkPoint>): Point2D? =
        centerOf(landmarks, PoseIndex.LEFT_KNEE, PoseIndex.RIGHT_KNEE)

    fun ankleCenter(landmarks: Map<PoseIndex, PoseLandmarkPoint>): Point2D? =
        centerOf(landmarks, PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE)

    private fun centerOf(
        landmarks: Map<PoseIndex, PoseLandmarkPoint>,
        firstIndex: PoseIndex,
        secondIndex: PoseIndex,
    ): Point2D? {
        val first = landmarks[firstIndex]
        val second = landmarks[secondIndex]
        if (!VisibilityChecker.isVisible(first) || !VisibilityChecker.isVisible(second)) {
            return null
        }
        return Point2D(
            x = (first!!.x + second!!.x) / 2.0,
            y = (first.y + second.y) / 2.0,
        )
    }
}
