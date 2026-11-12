package com.ratheshr8.beaconsimulator.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceEstimatorTest {
    @Test
    fun nearBeaconIsUnderOneMeter() {
        val meters = DistanceEstimator.estimateMeters(rssi = -55, txPower = -59)
        assertNotNull(meters)
        assertTrue(meters!! < 1.5)
    }

    @Test
    fun formatHandlesNull() {
        assertEquals("—", DistanceEstimator.formatDistance(null))
    }
}
