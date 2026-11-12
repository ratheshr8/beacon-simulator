package com.ratheshr8.beaconsimulator.floorplan

import java.util.UUID

/**
 * Beacon marker on a floor plan. Coordinates are normalized (0..1) relative to the image.
 */
data class PlacedBeacon(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val uuid: UUID,
    val major: Int,
    val minor: Int,
    val x: Float,
    val y: Float,
)

data class FloorPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Absolute path under app filesDir, or null for blank grid plan. */
    val imagePath: String? = null,
    /** Real-world width of the plan image in meters (used for distance overlay). */
    val widthMeters: Float = 20f,
    val beacons: List<PlacedBeacon> = emptyList(),
)

data class Point2d(val x: Float, val y: Float)

data class LocatedBeaconStatus(
    val placed: PlacedBeacon,
    val detected: Boolean,
    val rssi: Int?,
    val distanceMeters: Double?,
)

data class UserLocationEstimate(
    val point: Point2d,
    /** Normalized 0..1 confidence heuristic based on beacon count / distance quality. */
    val confidence: Float,
    val usedBeaconCount: Int,
)

enum class FloorPlanTool {
    VIEW,
    PLACE,
    LOCATE,
}

enum class ReceiverTab {
    SCAN,
    FLOOR_PLAN,
}
