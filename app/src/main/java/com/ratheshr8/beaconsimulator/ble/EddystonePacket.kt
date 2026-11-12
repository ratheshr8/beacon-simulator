package com.ratheshr8.beaconsimulator.ble

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Eddystone-UID frame helpers (Google Service UUID 0xFEAA).
 *
 * Frame: [Frame type 0x00][TX power][Namespace 10][Instance 6]
 */
object EddystonePacket {
    val SERVICE_UUID: android.os.ParcelUuid =
        android.os.ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

    private const val FRAME_UID: Byte = 0x00

    fun buildUidServiceData(
        proximityUuid: UUID,
        major: Int,
        minor: Int,
        txPower: Int,
    ): ByteArray {
        // Derive a stable 10-byte namespace + 6-byte instance from iBeacon fields
        // so one form can drive both formats.
        val namespace = ByteArray(10)
        val uuidBytes = ByteBuffer.allocate(16)
            .putLong(proximityUuid.mostSignificantBits)
            .putLong(proximityUuid.leastSignificantBits)
            .array()
        System.arraycopy(uuidBytes, 0, namespace, 0, 10)

        val instance = ByteArray(6)
        instance[0] = ((major shr 8) and 0xFF).toByte()
        instance[1] = (major and 0xFF).toByte()
        instance[2] = ((minor shr 8) and 0xFF).toByte()
        instance[3] = (minor and 0xFF).toByte()
        instance[4] = 0x00
        instance[5] = 0x01

        return ByteBuffer.allocate(18)
            .put(FRAME_UID)
            .put(txPower.toByte())
            .put(namespace)
            .put(instance)
            .array()
    }
}
