package com.ratheshr8.beaconsimulator.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.SparseArray
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

data class NearbyBleDevice(
    val deviceAddress: String,
    val name: String?,
    val rssi: Int,
    val hasManufacturerData: Boolean,
    val manufacturerCompanyIds: List<Int>,
    val lastSeenEpochMs: Long,
)

data class ScanFilterConfig(
    val uuid: UUID? = null,
    val major: Int? = null,
    val minor: Int? = null,
)

data class ScanStats(
    val rawAdvertisementCount: Int = 0,
    val uniqueDevicesSeen: Int = 0,
    val iBeaconCount: Int = 0,
)

class BeaconScanner(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private val beacons = ConcurrentHashMap<String, ScannedBeacon>()
    private val nearby = ConcurrentHashMap<String, NearbyBleDevice>()
    private val rawAdCount = AtomicInteger(0)
    private var scanCallback: ScanCallback? = null
    private var scanning = false
    private var filter = ScanFilterConfig()
    private var listener: ((List<ScannedBeacon>, List<NearbyBleDevice>, ScanStats) -> Unit)? = null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true
    fun isScanning(): Boolean = scanning

    fun setFilter(config: ScanFilterConfig) {
        filter = config
        emit()
    }

    fun setListener(listener: ((List<ScannedBeacon>, List<NearbyBleDevice>, ScanStats) -> Unit)?) {
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

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        }
        val settings = settingsBuilder.build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result != null) handleResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { handleResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                onError(scanFailureMessage(errorCode))
            }
        }

        scanCallback = callback
        try {
            // null filters = hear everything (needed to diagnose empty iBeacon lists)
            scanner.startScan(null, settings, callback)
            scanning = true
            emit()
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
        nearby.clear()
        rawAdCount.set(0)
        emit()
    }

    @SuppressLint("MissingPermission")
    private fun handleResult(result: ScanResult) {
        rawAdCount.incrementAndGet()
        val address = result.device?.address ?: return
        val record = result.scanRecord
        val companyIds = manufacturerCompanyIds(record?.manufacturerSpecificData)
        nearby[address] = NearbyBleDevice(
            deviceAddress = address,
            name = record?.deviceName ?: result.device?.name,
            rssi = result.rssi,
            hasManufacturerData = companyIds.isNotEmpty(),
            manufacturerCompanyIds = companyIds,
            lastSeenEpochMs = System.currentTimeMillis(),
        )

        val parsed = parseIBeacon(record) ?: run {
            emit()
            return
        }

        // One row per beacon identity (uuid/major/minor), keep strongest RSSI address
        val identityKey = DeviceNameStore.keyFor(parsed.uuid, parsed.major, parsed.minor)
        val distance = DistanceEstimator.estimateMeters(result.rssi, parsed.txPower)
        val existing = beacons[identityKey]
        if (existing != null && result.rssi < existing.rssi - 3 &&
            System.currentTimeMillis() - existing.lastSeenEpochMs < 3_000L
        ) {
            // Ignore brief weaker sightings so the list doesn't thrash
            emit()
            return
        }
        beacons[identityKey] = ScannedBeacon(
            deviceAddress = address,
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

    private fun parseIBeacon(record: android.bluetooth.le.ScanRecord?): IBeaconPacket.Parsed? {
        if (record == null) return null

        // Only accept Apple iBeacon or our compat company ID — avoids floods of false hits.
        IBeaconPacket.parseManufacturerPayload(
            record.getManufacturerSpecificData(IBeaconPacket.APPLE_COMPANY_ID)
        )?.let { return it }

        IBeaconPacket.parseManufacturerPayload(
            record.getManufacturerSpecificData(IBeaconPacket.COMPAT_COMPANY_ID)
        )?.let { return it }

        // Raw AD walk still limited to Apple 0x004C manufacturer structures.
        return IBeaconPacket.parseFromRawScanRecord(record.bytes)
    }

    private fun manufacturerCompanyIds(sparse: SparseArray<ByteArray>?): List<Int> {
        if (sparse == null || sparse.size() == 0) return emptyList()
        return buildList {
            for (i in 0 until sparse.size()) add(sparse.keyAt(i))
        }
    }

    private fun emit() {
        val beaconSnapshot = beacons.values
            .filter { matchesFilter(it) }
            .sortedWith(
                // Stable order by identity, then stronger signal — avoids jumpy selection
                compareBy<ScannedBeacon> { DeviceNameStore.keyFor(it.uuid, it.major, it.minor) }
                    .thenByDescending { it.rssi }
            )
        val nearbySnapshot = nearby.values
            .sortedWith(
                compareByDescending<NearbyBleDevice> { it.lastSeenEpochMs }
                    .thenByDescending { it.rssi }
            )
            .take(40)
        val stats = ScanStats(
            rawAdvertisementCount = rawAdCount.get(),
            uniqueDevicesSeen = nearby.size,
            iBeaconCount = beaconSnapshot.size,
        )
        listener?.invoke(beaconSnapshot, nearbySnapshot, stats)
    }

    private fun matchesFilter(beacon: ScannedBeacon): Boolean {
        val f = filter
        if (f.uuid != null && beacon.uuid != f.uuid) return false
        if (f.major != null && beacon.major != f.major) return false
        if (f.minor != null && beacon.minor != f.minor) return false
        return true
    }

    private fun scanFailureMessage(errorCode: Int): String = when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
            "Scan registration failed — toggle Bluetooth and retry"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning unsupported"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Bluetooth stack internal error"
        ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Out of BLE hardware resources"
        else -> "Scan failed (errorCode=$errorCode)"
    }
}
