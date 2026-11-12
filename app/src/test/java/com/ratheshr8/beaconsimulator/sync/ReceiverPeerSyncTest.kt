package com.ratheshr8.beaconsimulator.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiverPeerSyncTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val original = PeerSighting(
            receiverId = "abc",
            label = "Gate A",
            planId = "plan-1",
            pinX = 0.25f,
            pinY = 0.75f,
            beaconKey = "UUID|1|2",
            rssi = -70,
            distanceMeters = 3.5,
            timestampMs = 123L,
        )
        val parsed = ReceiverPeerSync.decode(ReceiverPeerSync.encode(original))
        assertNotNull(parsed)
        assertEquals(original.receiverId, parsed!!.receiverId)
        assertEquals(original.label, parsed.label)
        assertEquals(original.planId, parsed.planId)
        assertEquals(original.pinX, parsed.pinX, 0.0001f)
        assertEquals(original.distanceMeters, parsed.distanceMeters, 0.0001)
    }

    @Test
    fun rejectsBadPrefix() {
        assertNull(ReceiverPeerSync.decode("NOPE|data"))
    }
}
