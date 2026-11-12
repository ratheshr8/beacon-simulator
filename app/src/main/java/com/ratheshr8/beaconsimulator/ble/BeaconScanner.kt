package com.ratheshr8.beaconsimulator.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ScannedBeacon(
    val deviceAddress: String,
    val uuid: UUID,
    val major: Int,
    val minor: Int,
    val txPower: Int,
    val rssi: Int,
    val estimatedDistanceMeters: Double?,
    val lastSeenEpochMs: Long,
)

data class ScanFilterConfig(
    val uuid: UUID? = null,
    val major: Int? = null,
    val minor: Int? = null,
)

class BeaconScanner(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private val beacons = ConcurrentHashMap<String, ScannedBeacon>()
    private var scanCallback: ScanCallback? = null
    private var scanning = false
    private var filter = ScanFilterConfig()
    private var listener: ((List<ScannedBeacon>) -> Unit)? = null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true
    fun isScanning(): Boolean = scanning

    fun setFilter(config: ScanFilterConfig) {
        filter = config
        emit()
    }

    fun setListener(listener: ((List<ScannedBeacon>) -> Unit)?) {
        this.listener = listener
        emit()
    }

    @SuppressLint("MissingPermission")
    fun start(onError: (String) -> Unit) {
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            onError("Bluetooth is off or unavailable")
            return
        }
        if (scanning) return

        val scanner = bt.bluetoothLeScanner
        if (scanner == null) {
            onError("BluetoothLeScanner is null")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result != null) handleResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { handleResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                onError("Scan failed (errorCode=$errorCode)")
            }
        }

        scanCallback = callback
        try {
            scanner.startScan(null, settings, callback)
            scanning = true
        } catch (security: SecurityException) {
            scanning = false
            onError("Missing BLUETOOTH_SCAN / location permission: ${security.message}")
        } catch (ex: Exception) {
            scanning = false
            onError("Failed to start scan: ${ex.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val callback = scanCallback
        val scanner = adapter?.bluetoothLeScanner
        if (callback != null && scanner != null) {
            runCatching { scanner.stopScan(callback) }
        }
        scanCallback = null
        scanning = false
    }

    fun clear() {
        beacons.clear()
        emit()
    }

    private fun handleResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val payload = record.getManufacturerSpecificData(IBeaconPacket.APPLE_COMPANY_ID)
        val parsed = IBeaconPacket.parseManufacturerPayload(payload) ?: return

        val key = "${parsed.uuid}|${parsed.major}|${parsed.minor}|${result.device.address}"
        val distance = DistanceEstimator.estimateMeters(result.rssi, parsed.txPower)
        beacons[key] = ScannedBeacon(
            deviceAddress = result.device.address ?: "unknown",
            uuid = parsed.uuid,
            major = parsed.major,
            minor = parsed.minor,
            txPower = parsed.txPower,
            rssi = result.rssi,
            estimatedDistanceMeters = distance,
            lastSeenEpochMs = System.currentTimeMillis(),
        )
        emit()
    }

    private fun emit() {
        val snapshot = beacons.values
            .filter { matchesFilter(it) }
            .sortedWith(
                compareByDescending<ScannedBeacon> { it.lastSeenEpochMs }
                    .thenByDescending { it.rssi }
            )
        listener?.invoke(snapshot)
    }

    private fun matchesFilter(beacon: ScannedBeacon): Boolean {
        val f = filter
        if (f.uuid != null && beacon.uuid != f.uuid) return false
        if (f.major != null && beacon.major != f.major) return false
        if (f.minor != null && beacon.minor != f.minor) return false
        return true
    }
}
