package com.neurafit.app.analysis

import com.neurafit.app.pose.PoseIndex
import com.neurafit.app.pose.PoseLandmarkPoint
import kotlin.math.abs

object PoseFeatureExtractor {
    fun extract(landmarks: List<PoseLandmarkPoint>): BodyFeatures {
        val landmarksByIndex = landmarks.associateBy(PoseLandmarkPoint::index)

        val leftElbowAngle = angle(
            landmarksByIndex,
            PoseIndex.LEFT_SHOULDER,
            PoseIndex.LEFT_ELBOW,
            PoseIndex.LEFT_WRIST,
        )
        val rightElbowAngle = angle(
            landmarksByIndex,
            PoseIndex.RIGHT_SHOULDER,
            PoseIndex.RIGHT_ELBOW,
            PoseIndex.RIGHT_WRIST,
        )
        val leftShoulderAngle = angle(
            landmarksByIndex,
            PoseIndex.LEFT_HIP,
            PoseIndex.LEFT_SHOULDER,
            PoseIndex.LEFT_ELBOW,
        )
        val rightShoulderAngle = angle(
            landmarksByIndex,
            PoseIndex.RIGHT_HIP,
            PoseIndex.RIGHT_SHOULDER,
            PoseIndex.RIGHT_ELBOW,
        )
        val leftKneeAngle = angle(
            landmarksByIndex,
            PoseIndex.LEFT_HIP,
            PoseIndex.LEFT_KNEE,
            PoseIndex.LEFT_ANKLE,
        )
        val rightKneeAngle = angle(
            landmarksByIndex,
            PoseIndex.RIGHT_HIP,
            PoseIndex.RIGHT_KNEE,
            PoseIndex.RIGHT_ANKLE,
        )
        val leftHipAngle = angle(
            landmarksByIndex,
            PoseIndex.LEFT_SHOULDER,
            PoseIndex.LEFT_HIP,
            PoseIndex.LEFT_KNEE,
        )
        val rightHipAngle = angle(
            landmarksByIndex,
            PoseIndex.RIGHT_SHOULDER,
            PoseIndex.RIGHT_HIP,
            PoseIndex.RIGHT_KNEE,
        )
        val leftAnkleAngle = angle(
            landmarksByIndex,
            PoseIndex.LEFT_KNEE,
            PoseIndex.LEFT_ANKLE,
            PoseIndex.LEFT_FOOT_INDEX,
        )
        val rightAnkleAngle = angle(
            landmarksByIndex,
            PoseIndex.RIGHT_KNEE,
            PoseIndex.RIGHT_ANKLE,
            PoseIndex.RIGHT_FOOT_INDEX,
        )

        val shoulderCenter = BodyCenterCalculator.shoulderCenter(landmarksByIndex)
        val hipCenter = BodyCenterCalculator.hipCenter(landmarksByIndex)
        val kneeCenter = BodyCenterCalculator.kneeCenter(landmarksByIndex)
        val ankleCenter = BodyCenterCalculator.ankleCenter(landmarksByIndex)

        return BodyFeatures(
            leftElbowAngle = leftElbowAngle,
            rightElbowAngle = rightElbowAngle,
            leftShoulderAngle = leftShoulderAngle,
            rightShoulderAngle = rightShoulderAngle,
            leftKneeAngle = leftKneeAngle,
            rightKneeAngle = rightKneeAngle,
            leftHipAngle = leftHipAngle,
            rightHipAngle = rightHipAngle,
            leftAnkleAngle = leftAnkleAngle,
            rightAnkleAngle = rightAnkleAngle,
            shoulderCenter = shoulderCenter,
            hipCenter = hipCenter,
            kneeCenter = kneeCenter,
            ankleCenter = ankleCenter,
            torsoTiltAngle = if (shoulderCenter != null && hipCenter != null) {
                AngleCalculator.angleFromVertical(shoulderCenter, hipCenter)
            } else {
                null
            },
            bodyLineAngle = if (shoulderCenter != null && hipCenter != null && ankleCenter != null) {
                AngleCalculator.calculateAngle(shoulderCenter, hipCenter, ankleCenter)
            } else {
                null
            },
            shoulderHipDistance = distance(shoulderCenter, hipCenter),
            hipKneeDistance = distance(hipCenter, kneeCenter),
            kneeAnkleDistance = distance(kneeCenter, ankleCenter),
            leftRightKneeDistance = landmarkDistance(landmarksByIndex, PoseIndex.LEFT_KNEE, PoseIndex.RIGHT_KNEE),
            shoulderHeightDiff = heightDiff(landmarksByIndex, PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER),
            hipHeightDiff = heightDiff(landmarksByIndex, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP),
            kneeHeightDiff = heightDiff(landmarksByIndex, PoseIndex.LEFT_KNEE, PoseIndex.RIGHT_KNEE),
            ankleHeightDiff = heightDiff(landmarksByIndex, PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE),
            kneeAngleDiff = diff(leftKneeAngle, rightKneeAngle),
            elbowAngleDiff = diff(leftElbowAngle, rightElbowAngle),
            hipAngleDiff = diff(leftHipAngle, rightHipAngle),
        )
    }

    private fun angle(
        landmarks: Map<PoseIndex, PoseLandmarkPoint>,
        firstIndex: PoseIndex,
        centerIndex: PoseIndex,
        thirdIndex: PoseIndex,
    ): Double? {
        if (!VisibilityChecker.allVisible(landmarks, firstIndex, centerIndex, thirdIndex)) {
            return null
        }
        return AngleCalculator.calculateAngle(
            first = landmarks[firstIndex]!!.toPoint2D(),
            center = landmarks[centerIndex]!!.toPoint2D(),
            third = landmarks[thirdIndex]!!.toPoint2D(),
        )
    }

    private fun heightDiff(
        landmarks: Map<PoseIndex, PoseLandmarkPoint>,
        leftIndex: PoseIndex,
        rightIndex: PoseIndex,
    ): Double? {
        val left = landmarks[leftIndex]
        val right = landmarks[rightIndex]
        if (!VisibilityChecker.isVisible(left) || !VisibilityChecker.isVisible(right)) {
            return null
        }
        return left!!.y.toDouble() - right!!.y.toDouble()
    }

    private fun distance(first: Point2D?, second: Point2D?): Double? =
        if (first != null && second != null) first.distanceTo(second) else null

    private fun landmarkDistance(
        landmarks: Map<PoseIndex, PoseLandmarkPoint>,
        firstIndex: PoseIndex,
        secondIndex: PoseIndex,
    ): Double? {
        val first = landmarks[firstIndex]
        val second = landmarks[secondIndex]
        if (!VisibilityChecker.isVisible(first) || !VisibilityChecker.isVisible(second)) {
            return null
        }
        return first!!.toPoint2D().distanceTo(second!!.toPoint2D())
    }

    private fun diff(left: Double?, right: Double?): Double? =
        if (left != null && right != null) abs(left - right) else null

    private fun PoseLandmarkPoint.toPoint2D(): Point2D =
        Point2D(x = x.toDouble(), y = y.toDouble())
}
