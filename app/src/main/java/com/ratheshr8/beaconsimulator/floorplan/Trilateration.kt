package com.ratheshr8.beaconsimulator.floorplan

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Estimate a point from known anchors + distance radii (normalized plan units).
 * Uses a damped least-squares multilateration; falls back to weighted centroid.
 */
object Trilateration {
    data class Anchor(
        val x: Float,
        val y: Float,
        /** Distance in the same coordinate space as x/y (normalized plan units). */
        val radius: Float,
    )

    fun estimate(anchors: List<Anchor>): Point2d? {
        if (anchors.size < 2) return null
        if (anchors.size == 2) return estimateTwo(anchors[0], anchors[1])

        var x = anchors.map { it.x }.average().toFloat()
        var y = anchors.map { it.y }.average().toFloat()

        repeat(12) {
            var dx = 0f
            var dy = 0f
            anchors.forEach { a ->
                val dist = distance(x, y, a.x, a.y).coerceAtLeast(1e-4f)
                val err = dist - a.radius
                dx += ((a.x - x) / dist) * err
                dy += ((a.y - y) / dist) * err
            }
            val n = anchors.size.toFloat()
            x += dx / n
            y += dy / n
        }

        return Point2d(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }

    private fun estimateTwo(a: Anchor, b: Anchor): Point2d? {
        val d = distance(a.x, a.y, b.x, b.y)
        if (d < 1e-4f) return Point2d(a.x, a.y)
        val r0 = a.radius
        val r1 = b.radius
        var along = (r0.pow(2) - r1.pow(2) + d.pow(2)) / (2f * d)
        along = along.coerceIn(0f, d)
        val px = a.x + along * (b.x - a.x) / d
        val py = a.y + along * (b.y - a.y) / d
        val hSq = r0.pow(2) - along.pow(2)
        if (hSq <= 0f) return Point2d(px.coerceIn(0f, 1f), py.coerceIn(0f, 1f))
        // Without a third circle, stay on the line of centers.
        return Point2d(px.coerceIn(0f, 1f), py.coerceIn(0f, 1f))
    }

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))

    fun metersToNormalized(meters: Double, planWidthMeters: Float): Float {
        if (planWidthMeters <= 0f) return 0.05f
        return (meters / planWidthMeters).toFloat().coerceIn(0.01f, 1.5f)
    }
}
