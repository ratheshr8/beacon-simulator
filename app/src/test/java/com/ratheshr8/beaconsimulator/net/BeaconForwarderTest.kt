package com.ratheshr8.beaconsimulator.net

import com.ratheshr8.beaconsimulator.ble.ScannedBeacon
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class BeaconForwarderTest {
    @Test
    fun rejectsEmptyUrl() = runBlocking {
        val result = BeaconForwarder().postBeacons(
            endpointUrl = "   ",
            beacons = emptyList(),
        )
        assertTrue(result is ForwardResult.Failure)
    }

    @Test
    fun rejectsNonHttpUrl() = runBlocking {
        val beacon = ScannedBeacon(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            uuid = UUID.fromString("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0"),
            major = 1,
            minor = 2,
            txPower = -59,
            rssi = -60,
            estimatedDistanceMeters = 1.0,
            lastSeenEpochMs = 1L,
        )
        val result = BeaconForwarder().postBeacons("ftp://example.com/hook", listOf(beacon))
        assertTrue(result is ForwardResult.Failure)
    }
}
