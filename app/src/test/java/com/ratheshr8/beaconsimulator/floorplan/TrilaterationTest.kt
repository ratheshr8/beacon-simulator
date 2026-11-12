package com.ratheshr8.beaconsimulator.floorplan

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrilaterationTest {
    @Test
    fun estimatesNearCenterWithThreeAnchors() {
        val anchors = listOf(
            Trilateration.Anchor(0.2f, 0.2f, 0.28f),
            Trilateration.Anchor(0.8f, 0.2f, 0.28f),
            Trilateration.Anchor(0.5f, 0.8f, 0.35f),
        )
        val point = Trilateration.estimate(anchors)
        assertNotNull(point)
        assertTrue(point!!.x in 0.3f..0.7f)
        assertTrue(point.y in 0.2f..0.7f)
    }

    @Test
    fun metersToNormalizedScales() {
        val n = Trilateration.metersToNormalized(5.0, planWidthMeters = 20f)
        assertTrue(n in 0.24f..0.26f)
    }
}
