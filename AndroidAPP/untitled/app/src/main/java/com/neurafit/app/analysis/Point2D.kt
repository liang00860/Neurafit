package com.neurafit.app.analysis

import kotlin.math.hypot

data class Point2D(
    val x: Double,
    val y: Double,
) {
    fun distanceTo(other: Point2D): Double =
        hypot(x - other.x, y - other.y)
}
