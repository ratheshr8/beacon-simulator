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

    @Test
    fun parsePayloadWithCompanyIdPrefix() {
        val uuid = UUID.fromString("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0")
        val body = IBeaconPacket.buildManufacturerData(uuid, 1, 2, -59)
        val withPrefix = byteArrayOf(0x4C, 0x00) + body
        val parsed = IBeaconPacket.parseManufacturerPayload(withPrefix)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.major)
        assertEquals(2, parsed.minor)
    }

    @Test
    fun parseFromRawScanRecord() {
        val uuid = UUID.fromString("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0")
        val body = IBeaconPacket.buildManufacturerData(uuid, 9, 8, -55)
        // AD structure: length, type 0xFF, company 4C 00, payload…
        val ad = ByteArray(2 + 2 + body.size)
        ad[0] = (1 + 2 + body.size).toByte()
        ad[1] = 0xFF.toByte()
        ad[2] = 0x4C
        ad[3] = 0x00
        System.arraycopy(body, 0, ad, 4, body.size)
        val parsed = IBeaconPacket.parseFromRawScanRecord(ad)
        assertNotNull(parsed)
        assertEquals(9, parsed!!.major)
        assertEquals(8, parsed.minor)
        assertEquals(-55, parsed.txPower)
    }
}
