package com.ratheshr8.beaconsimulator.ble

import java.util.UUID

/**
 * Identity shared between Transmitter QR and Receiver pairing.
 */
data class BeaconIdentity(
    val uuid: UUID,
    val major: Int,
    val minor: Int,
    val label: String,
    val companyId: Int = IBeaconPacket.APPLE_COMPANY_ID,
    val txPower: Int = -59,
) {
    fun stableKey(): String = DeviceNameStore.keyFor(uuid, major, minor)

    fun toQrPayload(): String {
        // Avoid org.json so unit tests run without Android mocks.
        return """{"type":"$TYPE","v":1,"uuid":"${uuid.toString().uppercase()}","major":$major,"minor":$minor,"label":"${escape(label)}","companyId":$companyId,"txPower":$txPower}"""
    }

    companion object {
        const val TYPE = "beacon-simulator"

        fun parseQrPayload(raw: String): BeaconIdentity? = runCatching {
            val text = raw.trim()
            if (!text.contains("\"type\":\"$TYPE\"")) return null
            val uuid = IBeaconPacket.parseUuidOrNull(stringField(text, "uuid") ?: return null)
                ?: return null
            val major = intField(text, "major") ?: return null
            val minor = intField(text, "minor") ?: return null
            BeaconIdentity(
                uuid = uuid,
                major = major,
                minor = minor,
                label = stringField(text, "label") ?: "Paired beacon",
                companyId = intField(text, "companyId") ?: IBeaconPacket.APPLE_COMPANY_ID,
                txPower = intField(text, "txPower") ?: -59,
            )
        }.getOrNull()

        private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")

        private fun stringField(json: String, key: String): String? {
            val marker = "\"$key\":\""
            val start = json.indexOf(marker)
            if (start < 0) return null
            val from = start + marker.length
            val end = json.indexOf('"', from)
            if (end < 0) return null
            return json.substring(from, end)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }

        private fun intField(json: String, key: String): Int? {
            val marker = "\"$key\":"
            val start = json.indexOf(marker)
            if (start < 0) return null
            val from = start + marker.length
            val rest = json.substring(from)
            val token = rest.takeWhile { it.isDigit() || it == '-' }
            return token.toIntOrNull()
        }
    }
}
