package com.neurafit.app.realtime

import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.MotionCorrectionState
import com.neurafit.app.analysis.WorkoutActions
import com.neurafit.app.pose.PoseIndex
import org.json.JSONArray
import org.json.JSONObject

object PoseFramePayloadMapper {
    fun toJsonString(
        state: MotionCorrectionState,
        frameId: Long,
        timestampMs: Long = System.currentTimeMillis(),
        mirrored: Boolean = true,
        source: String = "android",
    ): String {
        val pose = state.poseOverlayResult
        val confidence = pose
            ?.landmarks
            ?.takeIf { it.isNotEmpty() }
            ?.map { it.confidence().toDouble() }
            ?.average()
            ?: 0.0

        return JSONObject()
            .put("type", "pose-frame")
            .put("version", 1)
            .put("timestamp", timestampMs)
            .put("frameId", frameId)
            .put("source", source)
            .put("actionName", state.actionName)
            .put("correctionMessage", state.correctionResult.message)
            .put(
                "image",
                JSONObject()
                    .put("width", pose?.imageWidth ?: 0)
                    .put("height", pose?.imageHeight ?: 0)
                    .put("mirrored", mirrored),
            )
            .put(
                "landmarks",
                JSONArray().apply {
                    pose?.landmarks.orEmpty().forEach { landmark ->
                        put(
                            JSONObject()
                                .put("id", landmark.index.value)
                                .put("x", landmark.x.toDouble())
                                .put("y", landmark.y.toDouble())
                                .put("z", JSONObject.NULL)
                                .put("score", landmark.confidence().toDouble()),
                        )
                    }
                },
            )
            .put("connections", skeletonConnectionsJson())
            .put("angles", anglesJson(state))
            .put("counters", countersJson(state))
            .put("score", state.correctionResult.score)
            .put("confidence", confidence)
            .put("advice", adviceFor(state.correctionResult.errors))
            .put("corrections", correctionsJson(state.correctionResult.errors))
            .toString()
    }

    private fun anglesJson(state: MotionCorrectionState): JSONObject {
        val features = state.bodyFeatures
        val pushUpFeatures = state.pushUpFeatures
        return JSONObject()
            .putNullable("leftKnee", features?.leftKneeAngle)
            .putNullable("rightKnee", features?.rightKneeAngle)
            .putNullable("leftHip", features?.leftHipAngle)
            .putNullable("rightHip", features?.rightHipAngle)
            .putNullable("leftElbow", features?.leftElbowAngle ?: pushUpFeatures?.leftElbowAngle)
            .putNullable("rightElbow", features?.rightElbowAngle ?: pushUpFeatures?.rightElbowAngle)
            .putNullable("torsoTilt", features?.torsoTiltAngle)
            .putNullable("bodyLine", features?.bodyLineAngle ?: pushUpFeatures?.bodyLineAngle)
    }

    private fun countersJson(state: MotionCorrectionState): JSONObject =
        if (state.actionName == WorkoutActions.PUSH_UP) {
            JSONObject()
                .put("repCount", state.pushUpState.repetitionCount)
                .put("stage", state.pushUpState.stage.name)
                .putNullable("lastRepDurationMs", state.pushUpState.lastRepDurationMs)
        } else {
            JSONObject()
                .put("repCount", state.squatState.repetitionCount)
                .put("stage", state.squatState.stage.name)
                .put("lastRepDurationMs", JSONObject.NULL)
        }

    private fun correctionsJson(errors: Set<ActionErrorType>): JSONArray {
        val visibleErrors = errors.takeIf { it.isNotEmpty() } ?: setOf(ActionErrorType.NONE)
        return JSONArray().apply {
            visibleErrors.forEach { error ->
                put(
                    JSONObject()
                        .put("type", error.name)
                        .put("severity", error.severity())
                        .put("message", error.message()),
                )
            }
        }
    }

    private fun adviceFor(errors: Set<ActionErrorType>): String {
        val primary = errors.firstOrNull { it != ActionErrorType.NONE } ?: ActionErrorType.NONE
        return when (primary) {
            ActionErrorType.NONE -> "保持当前节奏，动作很稳定。"
            ActionErrorType.SQUAT_TOO_SHALLOW -> "下一次再蹲低一些，充分完成下蹲。"
            ActionErrorType.BACK_LEAN_TOO_MUCH -> "收紧核心，打开胸口，避免身体前倾。"
            ActionErrorType.KNEE_VALGUS -> "膝盖和脚尖保持同向，不要内扣。"
            ActionErrorType.PUSH_UP_TOO_SHALLOW -> "下放时再低一些，让胸部充分接近地面。"
            ActionErrorType.WAIST_COLLAPSE -> "收紧核心和臀部，保持身体成一直线。"
            ActionErrorType.HIPS_TOO_HIGH -> "稍微降低臀部，让肩、髋、踝尽量保持一线。"
            ActionErrorType.PUSH_UP_TOO_FAST -> "放慢节奏，下降和撑起都要控制住。"
        }
    }

    private fun ActionErrorType.message(): String =
        when (this) {
            ActionErrorType.NONE -> "动作稳定"
            ActionErrorType.SQUAT_TOO_SHALLOW -> "下蹲深度不足，继续向下坐，让大腿更接近平行地面。"
            ActionErrorType.BACK_LEAN_TOO_MUCH -> "背部前倾过多，收紧核心，保持胸口打开。"
            ActionErrorType.KNEE_VALGUS -> "膝盖有内扣趋势，主动让膝盖对准脚尖。"
            ActionErrorType.PUSH_UP_TOO_SHALLOW -> "下降幅度不足，继续降低身体，让肘部弯曲更充分。"
            ActionErrorType.WAIST_COLLAPSE -> "塌腰"
            ActionErrorType.HIPS_TOO_HIGH -> "臀部过高，降低髋部，让肩、髋、脚踝接近一条直线。"
            ActionErrorType.PUSH_UP_TOO_FAST -> "动作过快，放慢节奏，下降和撑起都要稳定控制。"
        }

    private fun ActionErrorType.severity(): String =
        when (this) {
            ActionErrorType.NONE -> "info"
            ActionErrorType.PUSH_UP_TOO_FAST -> "warning"
            else -> "warning"
        }

    private fun skeletonConnectionsJson(): JSONArray =
        JSONArray().apply {
            SKELETON_CONNECTIONS.forEach { (start, end) ->
                put(JSONArray().put(start.value).put(end.value))
            }
        }

    private fun JSONObject.putNullable(name: String, value: Double?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.putNullable(name: String, value: Long?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private val SKELETON_CONNECTIONS = listOf(
        PoseIndex.NOSE to PoseIndex.LEFT_EYE_INNER,
        PoseIndex.NOSE to PoseIndex.RIGHT_EYE_INNER,
        PoseIndex.LEFT_EYE_INNER to PoseIndex.LEFT_EYE,
        PoseIndex.LEFT_EYE to PoseIndex.LEFT_EYE_OUTER,
        PoseIndex.RIGHT_EYE_INNER to PoseIndex.RIGHT_EYE,
        PoseIndex.RIGHT_EYE to PoseIndex.RIGHT_EYE_OUTER,
        PoseIndex.LEFT_EYE_OUTER to PoseIndex.LEFT_EAR,
        PoseIndex.RIGHT_EYE_OUTER to PoseIndex.RIGHT_EAR,
        PoseIndex.MOUTH_LEFT to PoseIndex.MOUTH_RIGHT,
        PoseIndex.LEFT_SHOULDER to PoseIndex.RIGHT_SHOULDER,
        PoseIndex.LEFT_SHOULDER to PoseIndex.LEFT_HIP,
        PoseIndex.RIGHT_SHOULDER to PoseIndex.RIGHT_HIP,
        PoseIndex.LEFT_HIP to PoseIndex.RIGHT_HIP,
        PoseIndex.LEFT_SHOULDER to PoseIndex.LEFT_ELBOW,
        PoseIndex.LEFT_ELBOW to PoseIndex.LEFT_WRIST,
        PoseIndex.LEFT_WRIST to PoseIndex.LEFT_PINKY,
        PoseIndex.LEFT_WRIST to PoseIndex.LEFT_INDEX,
        PoseIndex.LEFT_WRIST to PoseIndex.LEFT_THUMB,
        PoseIndex.LEFT_PINKY to PoseIndex.LEFT_INDEX,
        PoseIndex.RIGHT_SHOULDER to PoseIndex.RIGHT_ELBOW,
        PoseIndex.RIGHT_ELBOW to PoseIndex.RIGHT_WRIST,
        PoseIndex.RIGHT_WRIST to PoseIndex.RIGHT_PINKY,
        PoseIndex.RIGHT_WRIST to PoseIndex.RIGHT_INDEX,
        PoseIndex.RIGHT_WRIST to PoseIndex.RIGHT_THUMB,
        PoseIndex.RIGHT_PINKY to PoseIndex.RIGHT_INDEX,
        PoseIndex.LEFT_HIP to PoseIndex.LEFT_KNEE,
        PoseIndex.LEFT_KNEE to PoseIndex.LEFT_ANKLE,
        PoseIndex.LEFT_ANKLE to PoseIndex.LEFT_HEEL,
        PoseIndex.LEFT_HEEL to PoseIndex.LEFT_FOOT_INDEX,
        PoseIndex.LEFT_ANKLE to PoseIndex.LEFT_FOOT_INDEX,
        PoseIndex.RIGHT_HIP to PoseIndex.RIGHT_KNEE,
        PoseIndex.RIGHT_KNEE to PoseIndex.RIGHT_ANKLE,
        PoseIndex.RIGHT_ANKLE to PoseIndex.RIGHT_HEEL,
        PoseIndex.RIGHT_HEEL to PoseIndex.RIGHT_FOOT_INDEX,
        PoseIndex.RIGHT_ANKLE to PoseIndex.RIGHT_FOOT_INDEX,
    )
}
