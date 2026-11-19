package com.neurafit.app.analysis

import android.os.SystemClock

data class PushUpCounterSnapshot(
    val repetitionCount: Int,
    val stage: PushUpStage,
    val currentElbowAngle: Double?,
    val rawElbowAngle: Double?,
    val currentBodyDescent: Double?,
    val maxBodyDescentInCurrentRep: Double?,
    val currentRepMinimumElbowAngle: Double?,
    val lastRepMinimumElbowAngle: Double?,
    val lastRepMaxBodyDescent: Double?,
    val currentRepDurationMs: Long?,
    val lastRepDurationMs: Long?,
    val lastRepTooFast: Boolean,
    val repMinimumElbowAngles: List<Double>,
)

class PushUpCounter(
    private val armsStraightAngle: Double = 160.0,
    private val startDownAngle: Double = 150.0,
    private val bottomAngle: Double = 105.0,
    private val bottomExitAngle: Double = 122.0,
    private val smoothingFactor: Double = 0.35,
    private val maxMissingFrames: Int = 12,
    private val minRepDurationMs: Long = PushUpThreshold().minRepDurationMs,
) {
    private var repetitionCount = 0
    private var stage = PushUpStage.ARMS_STRAIGHT
    private var smoothedElbowAngle: Double? = null
    private var rawElbowAngle: Double? = null
    private var referenceShoulderYValue: Double? = null
    private var currentBodyDescent: Double? = null
    private var maxBodyDescentInCurrentRep: Double? = null
    private var currentRepMinimumElbowAngle: Double? = null
    private var lastRepMinimumElbowAngle: Double? = null
    private var lastRepMaxBodyDescent: Double? = null
    private var currentRepStartedAtMs: Long? = null
    private var lastRepDurationMs: Long? = null
    private var lastRepTooFast = false
    private var reachedBottomInCurrentRep = false
    private var missingFrameCount = 0
    private val repMinimumElbowAngles = mutableListOf<Double>()

    val referenceShoulderY: Double?
        get() = referenceShoulderYValue

    val snapshot: PushUpCounterSnapshot
        get() = PushUpCounterSnapshot(
            repetitionCount = repetitionCount,
            stage = stage,
            currentElbowAngle = smoothedElbowAngle,
            rawElbowAngle = rawElbowAngle,
            currentBodyDescent = currentBodyDescent,
            maxBodyDescentInCurrentRep = maxBodyDescentInCurrentRep,
            currentRepMinimumElbowAngle = currentRepMinimumElbowAngle,
            lastRepMinimumElbowAngle = lastRepMinimumElbowAngle,
            lastRepMaxBodyDescent = lastRepMaxBodyDescent,
            currentRepDurationMs = currentRepStartedAtMs?.let { SystemClock.elapsedRealtime() - it },
            lastRepDurationMs = lastRepDurationMs,
            lastRepTooFast = lastRepTooFast,
            repMinimumElbowAngles = repMinimumElbowAngles.toList(),
        )

    fun update(features: PushUpFeatures?): PushUpCounterSnapshot {
        val elbowAngle = features?.averageElbowAngle()
        if (features == null || elbowAngle == null) {
            markMissingFrame()
            return snapshot
        }

        missingFrameCount = 0
        rawElbowAngle = elbowAngle
        smoothedElbowAngle = smooth(smoothedElbowAngle, elbowAngle)
        updateReferenceShoulderY(features, smoothedElbowAngle)
        currentBodyDescent = calculateBodyDescent(features)

        val currentAngle = smoothedElbowAngle ?: elbowAngle
        updateRepExtremes(currentAngle, currentBodyDescent)

        when (stage) {
            PushUpStage.ARMS_STRAIGHT -> updateArmsStraight(currentAngle)
            PushUpStage.GOING_DOWN -> updateGoingDown(currentAngle)
            PushUpStage.BOTTOM -> updateBottom(currentAngle)
            PushUpStage.PUSHING_UP -> updatePushingUp(currentAngle)
        }

        return snapshot
    }

    fun reset(): PushUpCounterSnapshot {
        repetitionCount = 0
        stage = PushUpStage.ARMS_STRAIGHT
        smoothedElbowAngle = null
        rawElbowAngle = null
        referenceShoulderYValue = null
        currentBodyDescent = null
        maxBodyDescentInCurrentRep = null
        currentRepMinimumElbowAngle = null
        lastRepMinimumElbowAngle = null
        lastRepMaxBodyDescent = null
        currentRepStartedAtMs = null
        lastRepDurationMs = null
        lastRepTooFast = false
        reachedBottomInCurrentRep = false
        missingFrameCount = 0
        repMinimumElbowAngles.clear()
        return snapshot
    }

    private fun updateArmsStraight(elbowAngle: Double) {
        if (elbowAngle < startDownAngle) {
            stage = PushUpStage.GOING_DOWN
            currentRepStartedAtMs = SystemClock.elapsedRealtime()
            currentRepMinimumElbowAngle = elbowAngle
            maxBodyDescentInCurrentRep = currentBodyDescent
            reachedBottomInCurrentRep = false
            lastRepTooFast = false
        }
    }

    private fun updateGoingDown(elbowAngle: Double) {
        when {
            elbowAngle <= bottomAngle -> {
                stage = PushUpStage.BOTTOM
                reachedBottomInCurrentRep = true
            }
            elbowAngle >= armsStraightAngle && !reachedBottomInCurrentRep -> cancelCurrentRep()
        }
    }

    private fun updateBottom(elbowAngle: Double) {
        if (elbowAngle >= bottomExitAngle) {
            stage = PushUpStage.PUSHING_UP
        }
    }

    private fun updatePushingUp(elbowAngle: Double) {
        when {
            elbowAngle <= bottomAngle -> {
                stage = PushUpStage.BOTTOM
                reachedBottomInCurrentRep = true
            }
            elbowAngle >= armsStraightAngle -> completeRep()
        }
    }

    private fun completeRep() {
        if (reachedBottomInCurrentRep) {
            repetitionCount += 1
            currentRepMinimumElbowAngle?.let { repMinimumElbowAngles += it }
            lastRepMinimumElbowAngle = currentRepMinimumElbowAngle
            lastRepMaxBodyDescent = maxBodyDescentInCurrentRep
            val duration = currentRepStartedAtMs?.let { SystemClock.elapsedRealtime() - it }
            lastRepDurationMs = duration
            lastRepTooFast = duration != null && duration < minRepDurationMs
        }

        stage = PushUpStage.ARMS_STRAIGHT
        currentRepStartedAtMs = null
        currentRepMinimumElbowAngle = null
        maxBodyDescentInCurrentRep = null
        reachedBottomInCurrentRep = false
    }

    private fun cancelCurrentRep() {
        stage = PushUpStage.ARMS_STRAIGHT
        currentRepStartedAtMs = null
        currentRepMinimumElbowAngle = null
        maxBodyDescentInCurrentRep = null
        reachedBottomInCurrentRep = false
    }

    private fun updateRepExtremes(elbowAngle: Double, bodyDescent: Double?) {
        if (stage == PushUpStage.ARMS_STRAIGHT) return

        currentRepMinimumElbowAngle = minOf(currentRepMinimumElbowAngle ?: elbowAngle, elbowAngle)
        bodyDescent?.let {
            maxBodyDescentInCurrentRep = maxOf(maxBodyDescentInCurrentRep ?: it, it)
        }
    }

    private fun updateReferenceShoulderY(features: PushUpFeatures, elbowAngle: Double?) {
        val shoulderY = features.shoulderY ?: return
        if (stage == PushUpStage.ARMS_STRAIGHT && (elbowAngle == null || elbowAngle >= armsStraightAngle)) {
            referenceShoulderYValue = minOf(referenceShoulderYValue ?: shoulderY, shoulderY)
        }
        if (referenceShoulderYValue == null) {
            referenceShoulderYValue = shoulderY
        }
    }

    private fun calculateBodyDescent(features: PushUpFeatures): Double? {
        features.bodyDescent?.let { return it }
        val shoulderY = features.shoulderY ?: return null
        val reference = referenceShoulderYValue ?: return null
        return shoulderY - reference
    }

    private fun markMissingFrame() {
        missingFrameCount += 1
        if (missingFrameCount > maxMissingFrames && stage != PushUpStage.ARMS_STRAIGHT) {
            cancelCurrentRep()
        }
    }

    private fun smooth(previous: Double?, raw: Double): Double =
        if (previous == null) raw else previous * (1 - smoothingFactor) + raw * smoothingFactor
}
