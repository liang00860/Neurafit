package com.neurafit.app.analysis

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.hypot

object AngleCalculator {
    fun calculateAngle(
        first: Point2D,
        center: Point2D,
        third: Point2D,
    ): Double? {
        val vectorA = Point2D(first.x - center.x, first.y - center.y)
        val vectorB = Point2D(third.x - center.x, third.y - center.y)
        val magnitudeA = hypot(vectorA.x, vectorA.y)
        val magnitudeB = hypot(vectorB.x, vectorB.y)

        if (magnitudeA == 0.0 || magnitudeB == 0.0) return null

        val cosine = ((vectorA.x * vectorB.x) + (vectorA.y * vectorB.y)) /
            (magnitudeA * magnitudeB)
        return acos(cosine.coerceIn(-1.0, 1.0)).toDegrees()
    }

    fun angleFromVertical(
        top: Point2D,
        bottom: Point2D,
    ): Double? {
        val verticalReference = Point2D(bottom.x, bottom.y - 1.0)
        return calculateAngle(verticalReference, bottom, top)
    }

    private fun Double.toDegrees(): Double = this * 180.0 / PI
}
