package com.ratheshr8.beaconsimulator.ble

import kotlin.math.pow

object DistanceEstimator {
    /**
     * Rough distance estimate using the common log-distance path-loss model
     * used by many iBeacon SDKs. Accuracy varies wildly indoors.
     *
     * @param rssi measured RSSI
     * @param txPower calibrated RSSI at 1 meter (iBeacon measured power)
     */
    fun estimateMeters(rssi: Int, txPower: Int): Double? {
        if (rssi == 0) return null
        val ratio = rssi.toDouble() * 1.0 / txPower.toDouble()
        return if (ratio < 1.0) {
            ratio.pow(10.0)
        } else {
            0.89976 * ratio.pow(7.7095) + 0.111
        }
    }

    fun formatDistance(meters: Double?): String {
        if (meters == null || meters.isNaN() || meters.isInfinite()) return "—"
        return when {
            meters < 0.1 -> "< 0.1 m"
            meters < 10 -> String.format("%.1f m", meters)
            else -> String.format("%.0f m", meters)
        }
    }

    fun proximityLabel(meters: Double?): String = when {
        meters == null -> "Unknown"
        meters < 0.5 -> "Immediate"
        meters < 3.0 -> "Near"
        meters < 10.0 -> "Far"
        else -> "Very far"
    }
}
