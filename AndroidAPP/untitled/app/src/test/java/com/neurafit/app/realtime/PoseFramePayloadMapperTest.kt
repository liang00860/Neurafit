package com.neurafit.app.realtime

import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.BodyFeatures
import com.neurafit.app.analysis.CorrectionResult
import com.neurafit.app.analysis.MotionCorrectionState
import com.neurafit.app.analysis.Point2D
import com.neurafit.app.analysis.PushUpCounterSnapshot
import com.neurafit.app.analysis.PushUpFeatures
import com.neurafit.app.analysis.PushUpStage
import com.neurafit.app.analysis.SquatCounterSnapshot
import com.neurafit.app.analysis.SquatStage
import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.pose.PoseIndex
import com.neurafit.app.pose.PoseLandmarkPoint
import com.neurafit.app.pose.PoseOverlayResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseFramePayloadMapperTest {
    @Test
    fun noPoseFrameStillEmitsProtocolEnvelope() {
        val json = JSONObject(
            PoseFramePayloadMapper.toJsonString(
                state = MotionCorrectionState(),
                frameId = 7,
                timestampMs = 1_710_000_000_000L,
            ),
        )

        assertEquals("pose-frame", json.getString("type"))
        assertEquals(1, json.getInt("version"))
        assertEquals(7, json.getLong("frameId"))
        assertEquals(0, json.getJSONArray("landmarks").length())
        assertTrue(json.getJSONArray("connections").length() > 0)
    }

    @Test
    fun squatFrameIncludesAnglesCountersAndCorrection() {
        val state = MotionCorrectionState(
            poseOverlayResult = poseResult(),
            bodyFeatures = bodyFeatures(),
            actionName = WorkoutActions.SQUAT,
            squatState = SquatCounterSnapshot(
                repetitionCount = 3,
                stage = SquatStage.GOING_UP,
                currentKneeAngle = 91.0,
                rawKneeAngle = 93.0,
                kneeAngleSource = "both",
                currentHipDepth = 0.22,
                maxHipDepthInCurrentRep = 0.24,
                currentRepMinimumKneeAngle = 88.0,
                lastRepMinimumKneeAngle = 87.0,
                repMinimumKneeAngles = listOf(90.0, 88.0, 87.0),
            ),
            correctionResult = CorrectionResult(
                score = 75,
                errors = setOf(ActionErrorType.KNEE_VALGUS),
                message = "Knees are moving inward.",
            ),
        )

        val json = JSONObject(PoseFramePayloadMapper.toJsonString(state, frameId = 8))

        assertEquals(720, json.getJSONObject("image").getInt("width"))
        assertEquals(91.0, json.getJSONObject("angles").getDouble("leftKnee"), 0.01)
        assertEquals(3, json.getJSONObject("counters").getInt("repCount"))
        assertEquals("GOING_UP", json.getJSONObject("counters").getString("stage"))
        assertEquals("KNEE_VALGUS", json.getJSONArray("corrections").getJSONObject(0).getString("type"))
    }

    @Test
    fun pushUpFrameUsesPushUpCountersAndAngles() {
        val state = MotionCorrectionState(
            actionName = WorkoutActions.PUSH_UP,
            pushUpFeatures = PushUpFeatures(
                leftElbowAngle = 104.0,
                rightElbowAngle = 106.0,
                bodyLineAngle = 166.0,
                bodyDescent = 0.22,
                shoulderY = 0.34,
                hipY = 0.5,
                ankleY = 0.72,
                hipOffsetFromBodyLine = 0.02,
            ),
            pushUpState = PushUpCounterSnapshot(
                repetitionCount = 5,
                stage = PushUpStage.BOTTOM,
                currentElbowAngle = 105.0,
                rawElbowAngle = 107.0,
                currentBodyDescent = 0.22,
                maxBodyDescentInCurrentRep = 0.25,
                currentRepMinimumElbowAngle = 104.0,
                lastRepMinimumElbowAngle = 103.0,
                lastRepMaxBodyDescent = 0.24,
                currentRepDurationMs = 820,
                lastRepDurationMs = 760,
                lastRepTooFast = true,
                repMinimumElbowAngles = listOf(103.0),
            ),
            correctionResult = CorrectionResult(
                score = 85,
                errors = setOf(ActionErrorType.PUSH_UP_TOO_FAST),
                message = "Slow down.",
            ),
        )

        val json = JSONObject(PoseFramePayloadMapper.toJsonString(state, frameId = 9))

        assertEquals("PUSH_UP", json.getString("actionName"))
        assertEquals(104.0, json.getJSONObject("angles").getDouble("leftElbow"), 0.01)
        assertEquals(5, json.getJSONObject("counters").getInt("repCount"))
        assertEquals(760, json.getJSONObject("counters").getLong("lastRepDurationMs"))
        assertEquals("PUSH_UP_TOO_FAST", json.getJSONArray("corrections").getJSONObject(0).getString("type"))
    }

    private fun poseResult(): PoseOverlayResult =
        PoseOverlayResult(
            imageHeight = 1280,
            imageWidth = 720,
            landmarks = listOf(
                PoseLandmarkPoint(PoseIndex.LEFT_SHOULDER, 0.42f, 0.28f, 0.95f, 0.97f),
                PoseLandmarkPoint(PoseIndex.RIGHT_SHOULDER, 0.58f, 0.28f, 0.95f, 0.97f),
                PoseLandmarkPoint(PoseIndex.LEFT_HIP, 0.44f, 0.52f, 0.95f, 0.97f),
                PoseLandmarkPoint(PoseIndex.RIGHT_HIP, 0.56f, 0.52f, 0.95f, 0.97f),
                PoseLandmarkPoint(PoseIndex.LEFT_KNEE, 0.42f, 0.72f, 0.95f, 0.97f),
                PoseLandmarkPoint(PoseIndex.RIGHT_KNEE, 0.58f, 0.72f, 0.95f, 0.97f),
            ),
        )

    private fun bodyFeatures(): BodyFeatures =
        BodyFeatures(
            leftElbowAngle = 162.0,
            rightElbowAngle = 161.0,
            leftShoulderAngle = null,
            rightShoulderAngle = null,
            leftKneeAngle = 91.0,
            rightKneeAngle = 94.0,
            leftHipAngle = 82.0,
            rightHipAngle = 84.0,
            leftAnkleAngle = null,
            rightAnkleAngle = null,
            shoulderCenter = Point2D(0.5, 0.28),
            hipCenter = Point2D(0.5, 0.52),
            kneeCenter = Point2D(0.5, 0.72),
            ankleCenter = Point2D(0.5, 0.9),
            torsoTiltAngle = 12.0,
            bodyLineAngle = 168.0,
            shoulderHipDistance = 0.24,
            hipKneeDistance = 0.2,
            kneeAnkleDistance = 0.18,
            leftRightKneeDistance = 0.16,
            shoulderHeightDiff = 0.0,
            hipHeightDiff = 0.0,
            kneeHeightDiff = 0.0,
            ankleHeightDiff = 0.0,
            kneeAngleDiff = 3.0,
            elbowAngleDiff = 1.0,
            hipAngleDiff = 2.0,
        )
}
