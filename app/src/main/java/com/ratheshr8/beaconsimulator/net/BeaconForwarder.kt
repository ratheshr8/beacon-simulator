package com.ratheshr8.beaconsimulator.net

import com.ratheshr8.beaconsimulator.ble.DistanceEstimator
import com.ratheshr8.beaconsimulator.ble.IBeaconPacket
import com.ratheshr8.beaconsimulator.ble.ScannedBeacon
import com.ratheshr8.beaconsimulator.floorplan.FloorPlan
import com.ratheshr8.beaconsimulator.floorplan.Point2d
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed class ForwardResult {
    data class Success(val httpCode: Int, val beaconCount: Int) : ForwardResult()
    data class Failure(val message: String) : ForwardResult()
}

data class LocationBeaconPayload(
    val label: String,
    val uuid: java.util.UUID,
    val major: Int,
    val minor: Int,
    val x: Float,
    val y: Float,
    val tracked: Boolean,
    val distanceMeters: Double? = null,
    val rssi: Int? = null,
    val confidence: Double? = null,
)

/**
 * POSTs scanned iBeacon snapshots and floor-plan locations as JSON to a webhook URL.
 *
 * Scan body:
 * {
 *   "source": "beacon-simulator",
 *   "event": "beacon_scan",
 *   "timestampMs": 0,
 *   "beacons": [ { uuid, major, minor, rssi, txPower, distanceMeters, proximity, deviceAddress, lastSeenEpochMs } ]
 * }
 *
 * Location body:
 * {
 *   "source": "beacon-simulator",
 *   "event": "beacon_location",
 *   "timestampMs": 0,
 *   "receiverId": "...",
 *   "receiverName": "...",
 *   "planId": "...",
 *   "planName": "...",
 *   "planWidthMeters": 20,
 *   "receiverPin": { "x": 0.2, "y": 0.3 },
 *   "beacons": [ { label, uuid, major, minor, x, y, tracked, distanceMeters, rssi, confidence } ]
 * }
 */
class BeaconForwarder {
    suspend fun postBeacons(endpointUrl: String, beacons: List<ScannedBeacon>): ForwardResult {
        validateUrl(endpointUrl)?.let { return it }
        return postJson(endpointUrl, buildScanPayload(beacons), beacons.size)
    }

    suspend fun postLocations(
        endpointUrl: String,
        receiverId: String,
        receiverName: String,
        plan: FloorPlan,
        receiverPin: Point2d?,
        beacons: List<LocationBeaconPayload>,
    ): ForwardResult {
        validateUrl(endpointUrl)?.let { return it }
        return postJson(
            endpointUrl,
            buildLocationPayload(
                receiverId = receiverId,
                receiverName = receiverName,
                plan = plan,
                receiverPin = receiverPin,
                beacons = beacons,
            ),
            beacons.size,
        )
    }

    private fun validateUrl(endpointUrl: String): ForwardResult.Failure? {
        val urlText = endpointUrl.trim()
        if (urlText.isEmpty()) return ForwardResult.Failure("Webhook URL is empty")
        if (!urlText.startsWith("http://") && !urlText.startsWith("https://")) {
            return ForwardResult.Failure("URL must start with http:// or https://")
        }
        return null
    }

    private suspend fun postJson(
        endpointUrl: String,
        payload: JSONObject,
        beaconCount: Int,
    ): ForwardResult =
        withContext(Dispatchers.IO) {
            val urlText = endpointUrl.trim()

            val body = payload.toString()
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "BeaconSimulator/1.5")
                }
                // Optional token query already embedded by the user; also honor X-Ingest-Token
                // if the URL contains ?token= so Floorcast can authenticate.
                val token = urlText.substringAfter("token=", missingDelimiterValue = "")
                    .substringBefore('&')
                    .takeIf { it.isNotBlank() && !it.contains("://") }
                if (!token.isNullOrBlank()) {
                    connection.setRequestProperty("X-Ingest-Token", token)
                }

                BufferedOutputStream(connection.outputStream).use { out ->
                    out.write(body.toByteArray(StandardCharsets.UTF_8))
                    out.flush()
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    ForwardResult.Success(httpCode = code, beaconCount = beaconCount)
                } else {
                    val errBody = runCatching {
                        (connection.errorStream ?: connection.inputStream)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?.take(200)
                    }.getOrNull()
                    ForwardResult.Failure(
                        "HTTP $code" + if (!errBody.isNullOrBlank()) ": $errBody" else ""
                    )
                }
            } catch (ex: Exception) {
                ForwardResult.Failure(ex.message ?: ex.javaClass.simpleName)
            } finally {
                connection?.disconnect()
            }
        }

    private fun buildScanPayload(beacons: List<ScannedBeacon>): JSONObject {
        val array = JSONArray()
        beacons.forEach { beacon ->
            array.put(
                JSONObject()
                    .put("uuid", IBeaconPacket.formatUuid(beacon.uuid))
                    .put("major", beacon.major)
                    .put("minor", beacon.minor)
                    .put("rssi", beacon.rssi)
                    .put("txPower", beacon.txPower)
                    .put(
                        "distanceMeters",
                        beacon.estimatedDistanceMeters?.let { Math.round(it * 1000.0) / 1000.0 }
                            ?: JSONObject.NULL,
                    )
                    .put("proximity", DistanceEstimator.proximityLabel(beacon.estimatedDistanceMeters))
                    .put("deviceAddress", beacon.deviceAddress)
                    .put("lastSeenEpochMs", beacon.lastSeenEpochMs),
            )
        }
        return JSONObject()
            .put("source", "beacon-simulator")
            .put("event", "beacon_scan")
            .put("timestampMs", System.currentTimeMillis())
            .put("beacons", array)
    }

    private fun buildLocationPayload(
        receiverId: String,
        receiverName: String,
        plan: FloorPlan,
        receiverPin: Point2d?,
        beacons: List<LocationBeaconPayload>,
    ): JSONObject {
        val array = JSONArray()
        beacons.forEach { beacon ->
            array.put(
                JSONObject()
                    .put("label", beacon.label)
                    .put("uuid", IBeaconPacket.formatUuid(beacon.uuid))
                    .put("major", beacon.major)
                    .put("minor", beacon.minor)
                    .put("x", Math.round(beacon.x * 10000.0) / 10000.0)
                    .put("y", Math.round(beacon.y * 10000.0) / 10000.0)
                    .put("tracked", beacon.tracked)
                    .put(
                        "distanceMeters",
                        beacon.distanceMeters?.let { Math.round(it * 1000.0) / 1000.0 }
                            ?: JSONObject.NULL,
                    )
                    .put("rssi", beacon.rssi ?: JSONObject.NULL)
                    .put("confidence", beacon.confidence ?: JSONObject.NULL),
            )
        }
        val pin = if (receiverPin != null) {
            JSONObject()
                .put("x", Math.round(receiverPin.x * 10000.0) / 10000.0)
                .put("y", Math.round(receiverPin.y * 10000.0) / 10000.0)
        } else {
            JSONObject.NULL
        }
        return JSONObject()
            .put("source", "beacon-simulator")
            .put("event", "beacon_location")
            .put("timestampMs", System.currentTimeMillis())
            .put("receiverId", receiverId)
            .put("receiverName", receiverName)
            .put("planId", plan.id)
            .put("planName", plan.name)
            .put("planWidthMeters", plan.widthMeters.toDouble())
            .put("receiverPin", pin)
            .put("beacons", array)
    }
}
