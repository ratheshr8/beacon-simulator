package com.ratheshr8.beaconsimulator.ble

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Apple iBeacon manufacturer payload helpers.
 *
 * Layout after Apple company ID (0x004C):
 *  0: type (0x02)
 *  1: length (0x15 = 21)
 *  2..17: proximity UUID
 *  18..19: major (big-endian)
 *  20..21: minor (big-endian)
 *  22: measured power (calibrated RSSI at 1 m, signed)
 */
object IBeaconPacket {
    const val APPLE_COMPANY_ID = 0x004C
    private const val IBEACON_TYPE: Byte = 0x02
    private const val IBEACON_LENGTH: Byte = 0x15

    data class Parsed(
        val uuid: UUID,
        val major: Int,
        val minor: Int,
        val txPower: Int,
    )

    fun buildManufacturerData(
        uuid: UUID,
        major: Int,
        minor: Int,
        txPower: Int,
    ): ByteArray {
        require(major in 0..0xFFFF) { "major must be 0..65535" }
        require(minor in 0..0xFFFF) { "minor must be 0..65535" }
        require(txPower in -128..127) { "txPower must be -128..127" }

        val buffer = ByteBuffer.allocate(23)
        buffer.put(IBEACON_TYPE)
        buffer.put(IBEACON_LENGTH)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        buffer.putShort(major.toShort())
        buffer.putShort(minor.toShort())
        buffer.put(txPower.toByte())
        return buffer.array()
    }

    /**
     * Parse manufacturer-specific data that may or may not include the company ID prefix.
     * Android ScanRecord.getManufacturerSpecificData(companyId) returns payload without company ID.
     */
    fun parseManufacturerPayload(payload: ByteArray?): Parsed? {
        if (payload == null || payload.size < 23) return null
        if (payload[0] != IBEACON_TYPE || payload[1] != IBEACON_LENGTH) return null

        val buffer = ByteBuffer.wrap(payload)
        buffer.position(2)
        val most = buffer.long
        val least = buffer.long
        val major = buffer.short.toInt() and 0xFFFF
        val minor = buffer.short.toInt() and 0xFFFF
        val txPower = buffer.get().toInt()
        return Parsed(UUID(most, least), major, minor, txPower)
    }

    fun formatUuid(uuid: UUID): String = uuid.toString().uppercase()

    fun parseUuidOrNull(raw: String): UUID? = runCatching {
        UUID.fromString(raw.trim())
    }.getOrNull()
}
