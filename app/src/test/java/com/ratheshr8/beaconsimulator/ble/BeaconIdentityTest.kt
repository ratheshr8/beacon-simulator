package com.ratheshr8.beaconsimulator.ble

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BeaconIdentityTest {
    @Test
    fun qrRoundTrip() {
        val id = BeaconIdentity(
            uuid = UUID.fromString("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0"),
            major = 3,
            minor = 9,
            label = "Lab phone",
            companyId = IBeaconPacket.COMPAT_COMPANY_ID,
            txPower = -59,
        )
        val parsed = BeaconIdentity.parseQrPayload(id.toQrPayload())
        assertNotNull(parsed)
        assertEquals(id.uuid, parsed!!.uuid)
        assertEquals(3, parsed.major)
        assertEquals(9, parsed.minor)
        assertEquals("Lab phone", parsed.label)
        assertEquals(IBeaconPacket.COMPAT_COMPANY_ID, parsed.companyId)
    }

    @Test
    fun rejectsUnknownType() {
        assertNull(BeaconIdentity.parseQrPayload("""{"type":"other","uuid":"E2C56DB5-DFFB-48D2-B060-D0F5A71096E0","major":1,"minor":1}"""))
    }
}
