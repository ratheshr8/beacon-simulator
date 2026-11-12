package com.ratheshr8.beaconsimulator.ble

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IBeaconPacketTest {
    @Test
    fun buildAndParseRoundTrip() {
        val uuid = UUID.fromString("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0")
        val payload = IBeaconPacket.buildManufacturerData(uuid, 42, 7, -59)
        assertEquals(23, payload.size)
        assertEquals(0x02.toByte(), payload[0])
        assertEquals(0x15.toByte(), payload[1])

        val parsed = IBeaconPacket.parseManufacturerPayload(payload)
        assertNotNull(parsed)
        assertEquals(uuid, parsed!!.uuid)
        assertEquals(42, parsed.major)
        assertEquals(7, parsed.minor)
        assertEquals(-59, parsed.txPower)
    }

    @Test
    fun rejectShortPayload() {
        assertNull(IBeaconPacket.parseManufacturerPayload(byteArrayOf(0x02, 0x15)))
    }

    @Test
    fun parseUuid() {
        assertNotNull(IBeaconPacket.parseUuidOrNull("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0"))
        assertNull(IBeaconPacket.parseUuidOrNull("not-a-uuid"))
    }
}
