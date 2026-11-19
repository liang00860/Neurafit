package com.neurafit.app.analysis

import kotlin.math.max
import kotlin.math.min

data class SquatCounterSnapshot(
    val repetitionCount: Int,
    val stage: SquatStage,
    val currentKneeAngle: Double?,
    val rawKneeAngle: Double?,
    val kneeAngleSource: String,
    val currentHipDepth: Double?,
    val maxHipDepthInCurrentRep: Double?,
    val currentRepMinimumKneeAngle: Double?,
    val lastRepMinimumKneeAngle: Double?,
    val repMinimumKneeAngles: List<Double>,
)

class SquatCounter(
    private val startDepthRatio: Double = 0.08,
    private val bottomDepthRatio: Double = 0.18,
    private val topReturnDepthRatio: Double = 0.07,
    private val bottomExitDepthRatio: Double = 0.13,
    private val minimumRepDepthRatio: Double = 0.16,
    private val bottomKneeAngle: Double = 130.0,
    private val topKneeAngle: Double = 150.0,
    private val smoothingFactor: Double = 0.35,
    private val maxMissingFrames: Int = 12,
) {
    private var repetitionCount = 0
    private var stage = SquatStage.STANDING
    private var currentKneeAngle: Double? = null
    private var rawKneeAngle: Double? = null
    private var kneeAngleSource: String = "none"
    private var currentHipY: Double? = null
    private var previousHipY: Double? = null
    private var standingHipY: Double? = null
    private var currentHipDepth: Double? = null
    private var maxHipDepthInCurrentRep: Double? = null
    private var currentRepMinimumKneeAngle: Double? = null
    private var lastRepMinimumKneeAngle: Double? = null
    private var missingFrameCount = 0
    private val repMinimumKneeAngles = mutableListOf<Double>()

    val snapshot: SquatCounterSnapshot
        get() = SquatCounterSnapshot(
            repetitionCount = repetitionCount,
            stage = stage,
            currentKneeAngle = currentKneeAngle,
            rawKneeAngle = rawKneeAngle,
            kneeAngleSource = kneeAngleSource,
            currentHipDepth = currentHipDepth,
            maxHipDepthInCurrentRep = maxHipDepthInCurrentRep,
            currentRepMinimumKneeAngle = currentRepMinimumKneeAngle,
            lastRepMinimumKneeAngle = lastRepMinimumKneeAngle,
            repMinimumKneeAngles = repMinimumKneeAngles.toList(),
        )

    fun update(features: BodyFeatures?): SquatCounterSnapshot {
        val sample = features?.toSquatSample()
        if (sample == null) {
            markMissingFrame()
            return snapshot
        }

        missingFrameCount = 0
        rawKneeAngle = sample.kneeAngle
        kneeAngleSource = sample.kneeAngleSource
        currentKneeAngle = sample.kneeAngle?.let { smooth(currentKneeAngle, it) }

        val smoothedHipY = smooth(currentHipY, sample.hipY)
        previousHipY = currentHipY
        currentHipY = smoothedHipY

        if (standingHipY == null) {
            standingHipY = smoothedHipY
        }

        val hipDepth = ((smoothedHipY - standingHipY!!) / sample.bodyScale).coerceAtLeast(0.0)
        currentHipDepth = hipDepth
        val hipVelocity = previousHipY?.let { smoothedHipY - it } ?: 0.0

        when (stage) {
            SquatStage.STANDING -> updateStanding(smoothedHipY, hipDepth, sample)
            SquatStage.GOING_DOWN -> updateGoingDown(hipDepth, hipVelocity, sample)
            SquatStage.BOTTOM -> updateBottom(hipDepth, hipVelocity, sample)
            SquatStage.GOING_UP -> updateGoingUp(smoothedHipY, hipDepth, sample)
        }

        return snapshot
    }

    fun reset(): SquatCounterSnapshot {
        repetitionCount = 0
        stage = SquatStage.STANDING
        currentKneeAngle = null
        rawKneeAngle = null
        kneeAngleSource = "none"
        currentHipY = null
        previousHipY = null
        standingHipY = null
        currentHipDepth = null
        maxHipDepthInCurrentRep = null
        currentRepMinimumKneeAngle = null
        lastRepMinimumKneeAngle = null
        missingFrameCount = 0
        repMinimumKneeAngles.clear()
        return snapshot
    }

    private fun updateStanding(
        hipY: Double,
        hipDepth: Double,
        sample: SquatSample,
    ) {
        if (!sample.isKneeBentForSquatStart) {
            standingHipY = min(standingHipY ?: hipY, hipY)
        }

        if (hipDepth >= startDepthRatio || sample.isKneeBentForSquatStart) {
            stage = SquatStage.GOING_DOWN
            maxHipDepthInCurrentRep = hipDepth
            currentRepMinimumKneeAngle = sample.kneeAngle
        }
    }

    private fun updateGoingDown(
        hipDepth: Double,
        hipVelocity: Double,
        sample: SquatSample,
    ) {
        updateRepExtremes(hipDepth, sample.kneeAngle)

        when {
            hasReachedBottom(hipDepth, sample.kneeAngle) -> stage = SquatStage.BOTTOM
            hasStartedMovingUpAfterEnoughDepth(hipVelocity) -> stage = SquatStage.BOTTOM
            hasReturnedToTop(hipDepth, sample.kneeAngle) -> cancelCurrentRep()
        }
    }

    private fun updateBottom(
        hipDepth: Double,
        hipVelocity: Double,
        sample: SquatSample,
    ) {
        updateRepExtremes(hipDepth, sample.kneeAngle)
        if (hipDepth <= bottomExitDepthRatio || hipVelocity < -sample.bodyScale * UP_VELOCITY_RATIO) {
            stage = SquatStage.GOING_UP
        }
    }

    private fun updateGoingUp(
        hipY: Double,
        hipDepth: Double,
        sample: SquatSample,
    ) {
        updateRepExtremes(hipDepth, sample.kneeAngle)

        when {
            hasReachedBottom(hipDepth, sample.kneeAngle) -> stage = SquatStage.BOTTOM
            hasReturnedToTop(hipDepth, sample.kneeAngle) -> completeRep(hipY)
        }
    }

    private fun updateRepExtremes(hipDepth: Double, kneeAngle: Double?) {
        maxHipDepthInCurrentRep = max(maxHipDepthInCurrentRep ?: hipDepth, hipDepth)
        if (kneeAngle != null) {
            currentRepMinimumKneeAngle = min(currentRepMinimumKneeAngle ?: kneeAngle, kneeAngle)
        }
    }

    private fun hasReachedBottom(hipDepth: Double, kneeAngle: Double?): Boolean =
        hipDepth >= bottomDepthRatio || (kneeAngle != null && kneeAngle <= bottomKneeAngle)

    private fun hasStartedMovingUpAfterEnoughDepth(hipVelocity: Double): Boolean {
        val maxDepth = maxHipDepthInCurrentRep ?: 0.0
        return maxDepth >= minimumRepDepthRatio && hipVelocity < 0.0
    }

    private fun hasReturnedToTop(hipDepth: Double, kneeAngle: Double?): Boolean =
        hipDepth <= topReturnDepthRatio || (kneeAngle != null && kneeAngle >= topKneeAngle)

    private fun completeRep(hipY: Double) {
        val maxDepth = maxHipDepthInCurrentRep ?: 0.0
        if (maxDepth >= minimumRepDepthRatio) {
            repetitionCount += 1
            val minimumKneeAngle = currentRepMinimumKneeAngle
            lastRepMinimumKneeAngle = minimumKneeAngle
            if (minimumKneeAngle != null) {
                repMinimumKneeAngles += minimumKneeAngle
            }
        }
        stage = SquatStage.STANDING
        standingHipY = min(standingHipY ?: hipY, hipY)
        maxHipDepthInCurrentRep = null
        currentRepMinimumKneeAngle = null
    }

    private fun cancelCurrentRep() {
        stage = SquatStage.STANDING
        maxHipDepthInCurrentRep = null
        currentRepMinimumKneeAngle = null
    }

    private fun markMissingFrame() {
        rawKneeAngle = null
        kneeAngleSource = "none"
        missingFrameCount += 1
        if (missingFrameCount > maxMissingFrames && stage != SquatStage.STANDING) {
            cancelCurrentRep()
        }
    }

    private fun smooth(previous: Double?, raw: Double): Double =
        if (previous == null) raw else previous + (raw - previous) * smoothingFactor

    private fun BodyFeatures.toSquatSample(): SquatSample? {
        val hipY = hipCenter?.y ?: return null
        val scale = bodyScale() ?: return null
        val kneeSample = kneeAngleSample()
        return SquatSample(
            hipY = hipY,
            bodyScale = scale,
            kneeAngle = kneeSample?.angle,
            kneeAngleSource = kneeSample?.source ?: "none",
        )
    }

    private fun BodyFeatures.bodyScale(): Double? {
        val legScale = listOfNotNull(hipKneeDistance, kneeAnkleDistance)
            .takeIf { it.isNotEmpty() }
            ?.sum()
        val fallbackScale = shoulderHipDistance?.times(2.0)
        return listOfNotNull(legScale, fallbackScale, DEFAULT_BODY_SCALE)
            .firstOrNull { it >= MIN_BODY_SCALE }
    }

    private fun BodyFeatures.kneeAngleSample(): KneeAngleSample? {
        val left = leftKneeAngle
        val right = rightKneeAngle
        return when {
            left != null && right != null -> KneeAngleSample((left + right) / 2.0, "both")
            left != null -> KneeAngleSample(left, "left")
            right != null -> KneeAngleSample(right, "right")
            else -> null
        }
    }

    private data class SquatSample(
        val hipY: Double,
        val bodyScale: Double,
        val kneeAngle: Double?,
        val kneeAngleSource: String,
    ) {
        val isKneeBentForSquatStart: Boolean =
            kneeAngle != null && kneeAngle < topKneeAngleForStart
    }

    private data class KneeAngleSample(
        val angle: Double,
        val source: String,
    )

    private companion object {
        const val DEFAULT_BODY_SCALE = 0.35
        const val MIN_BODY_SCALE = 0.08
        const val UP_VELOCITY_RATIO = 0.012
        const val topKneeAngleForStart = 150.0
    }
}
