package com.neurafit.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neurafit.app.analysis.PushUpThreshold
import com.neurafit.app.analysis.SquatThreshold

@Entity(tableName = "user_action_thresholds")
data class UserActionThreshold(
    @PrimaryKey
    val actionName: String,
    val maxKneeAngleAtBottom: Double,
    val maxBackLeanAngle: Double,
    val minKneeDistance: Double,
    val updatedAt: Long,
    val pushUpMaxElbowAngleAtBottom: Double = PushUpThreshold().maxElbowAngleAtBottom,
    val pushUpMinBodyLineAngle: Double = PushUpThreshold().minBodyLineAngle,
    val pushUpMinDescent: Double = PushUpThreshold().minDescent,
    val pushUpMinRepDurationMs: Long = PushUpThreshold().minRepDurationMs,
) {
    fun toSquatThreshold(): SquatThreshold =
        SquatThreshold(
            maxKneeAngleAtBottom = maxKneeAngleAtBottom,
            maxBackLeanAngle = maxBackLeanAngle,
            minKneeDistance = minKneeDistance,
        )

    fun toPushUpThreshold(): PushUpThreshold =
        PushUpThreshold(
            maxElbowAngleAtBottom = pushUpMaxElbowAngleAtBottom,
            minBodyLineAngle = pushUpMinBodyLineAngle,
            minDescent = pushUpMinDescent,
            minRepDurationMs = pushUpMinRepDurationMs,
        )

    companion object {
        fun defaultFor(
            actionName: String,
            updatedAt: Long = System.currentTimeMillis(),
        ): UserActionThreshold {
            val default = SquatThreshold()
            val pushUpDefault = PushUpThreshold()
            return UserActionThreshold(
                actionName = actionName,
                maxKneeAngleAtBottom = default.maxKneeAngleAtBottom,
                maxBackLeanAngle = default.maxBackLeanAngle,
                minKneeDistance = default.minKneeDistance,
                updatedAt = updatedAt,
                pushUpMaxElbowAngleAtBottom = pushUpDefault.maxElbowAngleAtBottom,
                pushUpMinBodyLineAngle = pushUpDefault.minBodyLineAngle,
                pushUpMinDescent = pushUpDefault.minDescent,
                pushUpMinRepDurationMs = pushUpDefault.minRepDurationMs,
            )
        }
    }
}
