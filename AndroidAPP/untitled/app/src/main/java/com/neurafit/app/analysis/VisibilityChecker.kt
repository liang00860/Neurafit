package com.neurafit.app.analysis

import com.neurafit.app.pose.PoseIndex
import com.neurafit.app.pose.PoseLandmarkPoint

object VisibilityChecker {
    const val DEFAULT_MIN_CONFIDENCE = 0.5f

    fun isVisible(
        landmark: PoseLandmarkPoint?,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    ): Boolean = landmark != null && landmark.confidence() >= minConfidence

    fun allVisible(
        landmarksByIndex: Map<PoseIndex, PoseLandmarkPoint>,
        vararg indexes: PoseIndex,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    ): Boolean = indexes.all { index ->
        isVisible(landmarksByIndex[index], minConfidence)
    }
}
