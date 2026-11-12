package com.ratheshr8.beaconsimulator.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    /** Alternate company ID when OEMs filter Apple 0x004C advertisements. */
    const val COMPAT_COMPANY_ID = 0x0BAF
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

        val buffer = ByteBuffer.allocate(23).order(ByteOrder.BIG_ENDIAN)
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
        if (payload == null) return null
        // Payload without company ID (normal Android API)
        parseAt(payload, 0)?.let { return it }
        // Some stacks leave 2-byte company ID (little-endian 4C 00) in front
        if (payload.size >= 25 &&
            (payload[0].toInt() and 0xFF) == 0x4C &&
            (payload[1].toInt() and 0xFF) == 0x00
        ) {
            parseAt(payload, 2)?.let { return it }
        }
        return null
    }

    /**
     * Scan raw AD bytes for an iBeacon frame (ff 4c 00 02 15 … or bare 02 15 …).
     */
    fun parseFromRawScanRecord(raw: ByteArray?): Parsed? {
        if (raw == null || raw.isEmpty()) return null
        // Prefer Apple manufacturer AD structure: len, 0xFF, 0x4C, 0x00, 0x02, 0x15, …
        var i = 0
        while (i < raw.size) {
            val len = raw[i].toInt() and 0xFF
            if (len == 0) break
            if (i + len >= raw.size) break
            val type = raw[i + 1].toInt() and 0xFF
            if (type == 0xFF && len >= 26) {
                val companyLow = raw[i + 2].toInt() and 0xFF
                val companyHigh = raw[i + 3].toInt() and 0xFF
                if (companyLow == 0x4C && companyHigh == 0x00) {
                    parseAt(raw, i + 4)?.let { return it }
                }
            }
            i += len + 1
        }
        // Fallback: search for 02 15 marker anywhere
        for (idx in 0 until raw.size - 22) {
            if (raw[idx] == IBEACON_TYPE && raw[idx + 1] == IBEACON_LENGTH) {
                parseAt(raw, idx)?.let { return it }
            }
        }
        return null
    }

    private fun parseAt(payload: ByteArray, offset: Int): Parsed? {
        if (payload.size < offset + 23) return null
        if (payload[offset] != IBEACON_TYPE || payload[offset + 1] != IBEACON_LENGTH) return null
        // slice() so position 0 is at payload[offset]
        val buffer = ByteBuffer.wrap(payload, offset, 23)
            .slice()
            .order(ByteOrder.BIG_ENDIAN)
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
